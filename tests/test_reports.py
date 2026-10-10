"""
Tests for Reporting Lost and Found Items.
Verifies report submission, browse catalog filters, report detail views, and status toggling.
"""

from datetime import date
from app.extensions import db
from app.models.report import Report


def test_report_creation_requires_login(client):
    """Verify unauthenticated users cannot submit reports and are redirected."""
    response = client.get("/reports/new")
    assert response.status_code == 302
    assert "/login" in response.headers["Location"]


def test_report_lost_item_creation(client, auth_student, app):
    """Verify authenticated student can submit a lost item report."""
    payload = {
        "title": "Lost Silver MacBook Air",
        "report_type": "lost",
        "category": "Laptops",
        "description": "Silver 13-inch MacBook Air left on desk 4 with stickers.",
        "date_incident": str(date.today()),
        "location": "Science Library 3rd Floor",
        "contact_info": "test.student@campus.edu",
    }
    response = client.post("/reports/new", data=payload, follow_redirects=True)
    assert response.status_code == 200

    with app.app_context():
        report = Report.query.filter_by(title="Lost Silver MacBook Air").first()
        assert report is not None
        assert report.report_type == "lost"
        assert report.category == "Laptops"
        assert report.status == "open"
        assert report.user_id == auth_student


def test_report_found_item_creation(client, auth_student, app):
    """Verify authenticated student can submit a found item report."""
    payload = {
        "title": "Found Car Key FOB",
        "report_type": "found",
        "category": "Keys",
        "description": "Black Honda car key found near parking lot B entrance.",
        "date_incident": str(date.today()),
        "location": "Parking Lot B",
        "contact_info": "Turned in to Lot B Security Booth",
    }
    response = client.post("/reports/new", data=payload, follow_redirects=True)
    assert response.status_code == 200

    with app.app_context():
        report = Report.query.filter_by(title="Found Car Key FOB").first()
        assert report is not None
        assert report.report_type == "found"
        assert report.category == "Keys"
        assert report.status == "open"


def test_browse_catalog_displays_reports(client, auth_student, app):
    """Verify /reports catalog lists submitted reports."""
    with app.app_context():
        r = Report(
            title="Catalog Test Item",
            report_type="lost",
            category="Books",
            description="Biology 101 textbook with highlighted pages.",
            location="Lecture Hall 1",
            contact_info="student@campus.edu",
            user_id=auth_student,
        )
        db.session.add(r)
        db.session.commit()

    response = client.get("/reports")
    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "Catalog Test Item" in html


def test_filter_reports_by_type(client, auth_student, app):
    """Verify filter parameters (type=lost vs type=found) work correctly."""
    with app.app_context():
        r1 = Report(
            title="Lost Blue Umbrella",
            report_type="lost",
            category="Other",
            description="Compact dark blue folding umbrella",
            location="Cafe",
            contact_info="email",
            user_id=auth_student,
        )
        r2 = Report(
            title="Found Gold Watch",
            report_type="found",
            category="Accessories",
            description="Wristwatch found in restroom",
            location="Restroom",
            contact_info="Security",
            user_id=auth_student,
        )
        db.session.add_all([r1, r2])
        db.session.commit()

    # Query only lost items
    res_lost = client.get("/reports?type=lost")
    html_lost = res_lost.get_data(as_text=True)
    assert "Lost Blue Umbrella" in html_lost
    assert "Found Gold Watch" not in html_lost

    # Query only found items
    res_found = client.get("/reports?type=found")
    html_found = res_found.get_data(as_text=True)
    assert "Found Gold Watch" in html_found
    assert "Lost Blue Umbrella" not in html_found


def test_report_toggle_status_by_owner(client, auth_student, app):
    """Verify owner can toggle report status between open and resolved."""
    with app.app_context():
        report = Report(
            title="Resolvable Item",
            report_type="lost",
            category="Wallets",
            description="Test wallet",
            location="Dorm",
            contact_info="contact",
            status="open",
            user_id=auth_student,
        )
        db.session.add(report)
        db.session.commit()
        report_id = report.id

    # Toggle to resolved
    res_toggle = client.post(f"/reports/{report_id}/toggle-status", follow_redirects=True)
    assert res_toggle.status_code == 200

    with app.app_context():
        updated = Report.query.get(report_id)
        assert updated.status == "resolved"


def test_unauthorized_user_cannot_delete_report(client, auth_admin, app):
    """Verify non-owner non-admin user cannot delete another student's report."""
    # Create another regular user
    with app.app_context():
        other_user = Report(
            title="Other Student Item",
            report_type="lost",
            category="Phones",
            description="Phone",
            location="Gym",
            contact_info="info",
            user_id=9999,  # Unrelated user
        )
        db.session.add(other_user)
        db.session.commit()
        target_id = other_user.id

    # Admin is authorized to delete
    res_admin_delete = client.post(f"/reports/{target_id}/delete", follow_redirects=True)
    assert res_admin_delete.status_code == 200

    with app.app_context():
        assert Report.query.get(target_id) is None
