from flask import Blueprint, render_template, current_app
from flask_login import login_required, current_user
from app.models.report import Report
from app.services.ai_service import ai_service

dashboard_bp = Blueprint("dashboard", __name__)


@dashboard_bp.route("/dashboard")
@login_required
def index():
    """Student dashboard displaying reports and active AI match suggestions."""
    my_reports = (
        Report.query.filter_by(user_id=current_user.id)
        .order_by(Report.created_at.desc())
        .all()
    )

    my_lost = [r for r in my_reports if r.is_lost]
    my_found = [r for r in my_reports if r.is_found]
    my_open = [r for r in my_reports if r.is_open]
    my_resolved = [r for r in my_reports if r.status == "resolved"]

    # Gather AI matches for open reports that have embeddings
    matches_by_report = {}
    if ai_service.is_available():
        threshold = current_app.config.get("AI_SIMILARITY_THRESHOLD", 0.60)
        for report in my_open:
            if report.has_embedding:
                opposite_type = "found" if report.is_lost else "lost"
                candidates = (
                    Report.query.filter(
                        Report.report_type == opposite_type,
                        Report.status == "open",
                        Report.id != report.id,
                        Report.embedding_json.isnot(None),
                    )
                    .order_by(Report.created_at.desc())
                    .limit(30)
                    .all()
                )
                matches = ai_service.find_potential_matches(
                    target_report=report,
                    candidates=candidates,
                    threshold=threshold,
                    limit=3,
                )
                if matches:
                    matches_by_report[report.id] = {
                        "report": report,
                        "matches": matches,
                    }

    return render_template(
        "dashboard/index.html",
        my_reports=my_reports,
        my_lost=my_lost,
        my_found=my_found,
        my_open=my_open,
        my_resolved=my_resolved,
        matches_by_report=matches_by_report,
        ai_available=ai_service.is_available(),
    )
