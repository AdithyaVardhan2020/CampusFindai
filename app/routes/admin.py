from flask import Blueprint, render_template, redirect, url_for, flash, request, abort, current_app
from flask_login import login_required, current_user
from app.extensions import db
from app.models.user import User
from app.models.report import Report
from app.services.ai_service import ai_service
from app.services.image_service import delete_image_file

admin_bp = Blueprint("admin", __name__, url_prefix="/admin")


@admin_bp.before_request
@login_required
def require_admin():
    """Ensure all admin endpoints require administrator privileges."""
    if not current_user.is_admin:
        abort(403)


@admin_bp.route("/")
def dashboard():
    """Administrative management dashboard."""
    # Campus-wide statistics
    total_users = User.query.count()
    total_students = User.query.filter_by(role="student").count()
    total_reports = Report.query.count()
    total_lost = Report.query.filter_by(report_type="lost").count()
    total_found = Report.query.filter_by(report_type="found").count()
    total_resolved = Report.query.filter_by(status="resolved").count()
    reports_with_images = Report.query.filter(Report.image_filename.isnot(None)).count()
    reports_with_embeddings = Report.query.filter(Report.embedding_json.isnot(None)).count()

    # Recent reports and users
    recent_reports = Report.query.order_by(Report.created_at.desc()).limit(15).all()
    recent_users = User.query.order_by(User.created_at.desc()).limit(15).all()

    # AI engine status
    ai_status = ai_service.get_status()

    return render_template(
        "admin/dashboard.html",
        total_users=total_users,
        total_students=total_students,
        total_reports=total_reports,
        total_lost=total_lost,
        total_found=total_found,
        total_resolved=total_resolved,
        reports_with_images=reports_with_images,
        reports_with_embeddings=reports_with_embeddings,
        recent_reports=recent_reports,
        recent_users=recent_users,
        ai_status=ai_status,
        db_engine=db.engine.name,
        using_sqlite=current_app.config.get("USING_SQLITE_FALLBACK", False),
    )


@admin_bp.route("/reports/<int:report_id>/delete", methods=["POST"])
def delete_report(report_id: int):
    """Admin endpoint to remove inappropriate or spam reports."""
    report = Report.query.get_or_404(report_id)
    upload_folder = current_app.config["UPLOAD_FOLDER"]

    if report.image_filename:
        delete_image_file(report.image_filename, upload_folder)

    db.session.delete(report)
    db.session.commit()
    flash(f"Report #{report.id} ('{report.title}') removed by administrator.", "info")
    return redirect(url_for("admin.dashboard"))


@admin_bp.route("/users/<int:user_id>/toggle-status", methods=["POST"])
def toggle_user_status(user_id: int):
    """Admin endpoint to activate or deactivate student accounts."""
    if user_id == current_user.id:
        flash("You cannot deactivate your own administrator account.", "warning")
        return redirect(url_for("admin.dashboard"))

    user = User.query.get_or_404(user_id)
    user.is_active = not user.is_active
    db.session.commit()

    state = "activated" if user.is_active else "deactivated"
    flash(f"User account '{user.username}' has been {state}.", "info")
    return redirect(url_for("admin.dashboard"))
