import os
from pathlib import Path
from flask import Blueprint, render_template, current_app, send_from_directory, jsonify, Response
from app.extensions import db
from app.models.report import Report, REPORT_CATEGORIES
from app.models.user import User
from app.services.ai_service import ai_service

main_bp = Blueprint("main", __name__)


@main_bp.route("/")
def index():
    """CampusFind landing page with system statistics and recent reports."""
    # Summary metrics
    total_reports = Report.query.count()
    total_lost = Report.query.filter_by(report_type="lost").count()
    total_found = Report.query.filter_by(report_type="found").count()
    total_resolved = Report.query.filter_by(status="resolved").count()

    # Recent items
    recent_lost = (
        Report.query.filter_by(report_type="lost", status="open")
        .order_by(Report.created_at.desc())
        .limit(4)
        .all()
    )
    recent_found = (
        Report.query.filter_by(report_type="found", status="open")
        .order_by(Report.created_at.desc())
        .limit(4)
        .all()
    )

    return render_template(
        "index.html",
        total_reports=total_reports,
        total_lost=total_lost,
        total_found=total_found,
        total_resolved=total_resolved,
        recent_lost=recent_lost,
        recent_found=recent_found,
        categories=REPORT_CATEGORIES,
    )


@main_bp.route("/about")
def about():
    """About page explaining project architecture, OpenCLIP AI matching, and safety notices."""
    ai_status = ai_service.get_status()
    return render_template("about.html", ai_status=ai_status)


@main_bp.route("/health")
def health():
    """Application health check endpoint returning database and AI service diagnostics."""
    db_status = "connected"
    try:
        db.session.execute(db.text("SELECT 1"))
    except Exception as exc:
        db_status = f"unhealthy: {str(exc)}"

    ai_status = ai_service.get_status()

    return jsonify({
        "status": "ok" if "unhealthy" not in db_status else "degraded",
        "database": {
            "status": db_status,
            "engine": db.engine.name,
            "using_sqlite_fallback": current_app.config.get("USING_SQLITE_FALLBACK", False),
        },
        "ai_engine": ai_status,
    })


@main_bp.route("/uploads/<path:filename>")
def serve_upload(filename: str):
    """
    Safely serve user-uploaded images.
    Falls back gracefully to the placeholder SVG if the file does not exist on disk,
    preventing 404 broken image icons on item cards.
    """
    upload_folder = current_app.config["UPLOAD_FOLDER"]
    safe_filename = os.path.basename(filename)
    file_path = Path(upload_folder) / safe_filename

    if file_path.is_file():
        return send_from_directory(upload_folder, safe_filename)

    # Graceful fallback to static placeholder
    static_images_dir = Path(current_app.root_path) / "static" / "images"
    return send_from_directory(str(static_images_dir), "placeholder.svg", mimetype="image/svg+xml")
