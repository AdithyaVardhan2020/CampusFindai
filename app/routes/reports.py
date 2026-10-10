from datetime import datetime
from flask import Blueprint, render_template, redirect, url_for, flash, request, abort, current_app
from flask_login import login_required, current_user
from app.extensions import db
from app.models.report import Report, REPORT_CATEGORIES, REPORT_TYPES, REPORT_STATUSES
from app.forms import ReportForm, ReportEditForm
from app.services.image_service import (
    validate_and_save_image,
    delete_image_file,
    get_image_file_path,
    ImageValidationError,
)
from app.services.ai_service import ai_service

reports_bp = Blueprint("reports", __name__)


@reports_bp.route("/reports")
def index():
    """Browse and filter lost and found reports."""
    # Query parameters
    keyword = request.args.get("q", "").strip()
    report_type = request.args.get("type", "").strip().lower()
    category = request.args.get("category", "").strip()
    status = request.args.get("status", "open").strip().lower()
    location = request.args.get("location", "").strip()

    query = Report.query

    # Apply filters
    if keyword:
        search_filter = (
            Report.title.ilike(f"%{keyword}%")
            | Report.description.ilike(f"%{keyword}%")
            | Report.location.ilike(f"%{keyword}%")
        )
        query = query.filter(search_filter)

    if report_type in REPORT_TYPES:
        query = query.filter(Report.report_type == report_type)

    if category in REPORT_CATEGORIES:
        query = query.filter(Report.category == category)

    if status and status != "all":
        if status in REPORT_STATUSES:
            query = query.filter(Report.status == status)

    if location:
        query = query.filter(Report.location.ilike(f"%{location}%"))

    # Pagination
    page = request.args.get("page", 1, type=int)
    per_page = 12
    reports_pagination = query.order_by(Report.created_at.desc()).paginate(
        page=page, per_page=per_page, error_out=False
    )

    return render_template(
        "reports/index.html",
        reports=reports_pagination.items,
        pagination=reports_pagination,
        categories=REPORT_CATEGORIES,
        selected_type=report_type,
        selected_category=category,
        selected_status=status,
        keyword=keyword,
        location=location,
    )


@reports_bp.route("/reports/new", methods=["GET", "POST"])
@login_required
def create():
    """Create a new lost or found report."""
    form = ReportForm()
    if form.validate_on_submit():
        image_filename = None
        embedding = None

        # Handle optional photo upload
        if form.image.data and form.image.data.filename:
            try:
                upload_folder = current_app.config["UPLOAD_FOLDER"]
                image_filename = validate_and_save_image(
                    file_storage=form.image.data,
                    upload_folder=upload_folder,
                    max_size_bytes=current_app.config["MAX_CONTENT_LENGTH"],
                )
                
                # Generate real OpenCLIP embedding if AI service is available
                img_path = get_image_file_path(image_filename, upload_folder)
                if img_path:
                    embedding = ai_service.generate_embedding(img_path)

            except ImageValidationError as exc:
                flash(f"Image error: {str(exc)}", "danger")
                return render_template("reports/create.html", form=form)
            except Exception as exc:
                flash(f"Failed to process image: {str(exc)}", "warning")

        # Create report record
        report = Report(
            title=form.title.data.strip(),
            report_type=form.report_type.data.strip().lower(),
            category=form.category.data.strip(),
            description=form.description.data.strip(),
            date_incident=form.date_incident.data,
            location=form.location.data.strip(),
            contact_info=form.contact_info.data.strip(),
            status="open",
            image_filename=image_filename,
            user_id=current_user.id,
        )

        if embedding:
            report.set_embedding(embedding)

        db.session.add(report)
        db.session.commit()

        if image_filename and not embedding:
            if not ai_service.is_available():
                flash(
                    "Report submitted successfully with image! Note: Visual AI matching is currently "
                    "in setup mode on the server. Image embeddings will be processed when the model is loaded.",
                    "info"
                )
            else:
                flash("Report created! (Embedding extraction skipped or unreadable).", "info")
        else:
            flash(f"Report for '{report.title}' created successfully!", "success")

        return redirect(url_for("reports.detail", report_id=report.id))

    return render_template("reports/create.html", form=form)


@reports_bp.route("/reports/<int:report_id>")
def detail(report_id: int):
    """View details of a report and show AI similarity suggestions."""
    report = Report.query.get_or_404(report_id)

    # Compute AI visual matches if report has an image embedding
    matches = []
    ai_status = ai_service.get_status()

    if report.has_embedding and ai_service.is_available():
        # Find candidates of opposite report type
        opposite_type = "found" if report.is_lost else "lost"
        candidates = (
            Report.query.filter(
                Report.report_type == opposite_type,
                Report.status == "open",
                Report.id != report.id,
                Report.embedding_json.isnot(None),
            )
            .order_by(Report.created_at.desc())
            .limit(50)
            .all()
        )

        threshold = current_app.config.get("AI_SIMILARITY_THRESHOLD", 0.60)
        max_matches = current_app.config.get("AI_MAX_MATCHES", 6)
        matches = ai_service.find_potential_matches(
            target_report=report,
            candidates=candidates,
            threshold=threshold,
            limit=max_matches,
        )

    # Check if current user is owner or admin
    can_modify = current_user.is_authenticated and (
        current_user.id == report.user_id or current_user.is_admin
    )

    return render_template(
        "reports/detail.html",
        report=report,
        matches=matches,
        can_modify=can_modify,
        ai_status=ai_status,
        similarity_threshold=current_app.config.get("AI_SIMILARITY_THRESHOLD", 0.60),
    )


@reports_bp.route("/reports/<int:report_id>/edit", methods=["GET", "POST"])
@login_required
def edit(report_id: int):
    """Edit an existing report (Author or Admin only)."""
    report = Report.query.get_or_404(report_id)

    if report.user_id != current_user.id and not current_user.is_admin:
        abort(403)

    form = ReportEditForm(obj=report)

    if form.validate_on_submit():
        report.title = form.title.data.strip()
        report.report_type = form.report_type.data.strip().lower()
        report.category = form.category.data.strip()
        report.description = form.description.data.strip()
        report.date_incident = form.date_incident.data
        report.location = form.location.data.strip()
        report.contact_info = form.contact_info.data.strip()
        report.status = form.status.data.strip().lower()

        # Handle photo replacement
        if form.image.data and form.image.data.filename:
            upload_folder = current_app.config["UPLOAD_FOLDER"]
            try:
                new_filename = validate_and_save_image(
                    file_storage=form.image.data,
                    upload_folder=upload_folder,
                    max_size_bytes=current_app.config["MAX_CONTENT_LENGTH"],
                )
                
                # Safely delete old image file if existed
                if report.image_filename:
                    delete_image_file(report.image_filename, upload_folder)

                report.image_filename = new_filename

                # Recalculate embedding
                img_path = get_image_file_path(new_filename, upload_folder)
                if img_path and ai_service.is_available():
                    embedding = ai_service.generate_embedding(img_path)
                    report.set_embedding(embedding)
                else:
                    report.set_embedding(None)

            except ImageValidationError as exc:
                flash(f"Image error: {str(exc)}", "danger")
                return render_template("reports/edit.html", form=form, report=report)

        db.session.commit()
        flash("Report updated successfully!", "success")
        return redirect(url_for("reports.detail", report_id=report.id))

    return render_template("reports/edit.html", form=form, report=report)


@reports_bp.route("/reports/<int:report_id>/delete", methods=["POST"])
@login_required
def delete(report_id: int):
    """Delete a report (Author or Admin only)."""
    report = Report.query.get_or_404(report_id)

    if report.user_id != current_user.id and not current_user.is_admin:
        abort(403)

    upload_folder = current_app.config["UPLOAD_FOLDER"]
    if report.image_filename:
        delete_image_file(report.image_filename, upload_folder)

    db.session.delete(report)
    db.session.commit()
    flash(f"Report '{report.title}' has been deleted.", "info")
    return redirect(url_for("reports.index"))


@reports_bp.route("/reports/<int:report_id>/toggle-status", methods=["POST"])
@login_required
def toggle_status(report_id: int):
    """Toggle report status between 'open' and 'resolved'."""
    report = Report.query.get_or_404(report_id)

    if report.user_id != current_user.id and not current_user.is_admin:
        abort(403)

    if report.status == "open":
        report.status = "resolved"
        flash("Report marked as Resolved! Item has been returned.", "success")
    else:
        report.status = "open"
        flash("Report reopened.", "info")

    db.session.commit()
    return redirect(url_for("reports.detail", report_id=report.id))
