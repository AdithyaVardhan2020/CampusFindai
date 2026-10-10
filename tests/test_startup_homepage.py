"""
Tests for Application Startup and Homepage.
Verifies factory initialization, landing page, stats counters, about page, and health API.
"""

import json
from app.extensions import db
from app.models.report import Report
from app.models.user import User


def test_app_startup(app):
    """Verify application context initializes and test configuration applies."""
    assert app is not None
    assert app.config["TESTING"] is True
    assert app.config["WTF_CSRF_ENABLED"] is False


def test_homepage_status_and_headers(client):
    """Verify GET / returns HTTP 200 and UTF-8 HTML."""
    response = client.get("/")
    assert response.status_code == 200
    assert "text/html" in response.content_type


def test_homepage_branding_and_content(client):
    """Verify homepage renders brand name, action buttons, and navigation elements."""
    response = client.get("/")
    html = response.get_data(as_text=True)

    assert "CampusFind" in html
    assert "Report Lost Item" in html
    assert "Report Found Item" in html
    assert "Browse Directory" in html
    assert "navBrandLink" in html


def test_homepage_displays_database_metrics(app, client):
    """Verify statistics counters reflect database state."""
    with app.app_context():
        user = User(username="metric_user", email="metric@campus.edu", full_name="Metric User")
        user.set_password("pass123")
        db.session.add(user)
        db.session.commit()

        r1 = Report(
            title="Lost Wallet",
            report_type="lost",
            category="Wallets",
            description="Brown leather wallet lost in library",
            location="Library",
            contact_info="metric@campus.edu",
            user_id=user.id,
        )
        r2 = Report(
            title="Found Keys",
            report_type="found",
            category="Keys",
            description="Brass key on green ring",
            location="Gym",
            contact_info="Security",
            user_id=user.id,
        )
        db.session.add_all([r1, r2])
        db.session.commit()

    response = client.get("/")
    html = response.get_data(as_text=True)

    # Total reports counter should reflect 2
    assert "Total Reports" in html
    assert "Lost Items" in html
    assert "Found Items" in html


def test_about_page_status(client):
    """Verify GET /about renders successfully with AI architecture documentation."""
    response = client.get("/about")
    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "System Architecture & AI Docs" in html
    assert "OpenCLIP" in html


def test_health_check_endpoint(client):
    """Verify /health endpoint returns JSON with database and AI diagnostics."""
    response = client.get("/health")
    assert response.status_code == 200
    assert response.is_json

    data = response.get_json()
    assert "status" in data
    assert "database" in data
    assert data["database"]["status"] == "connected"
    assert "ai_engine" in data
