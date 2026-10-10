import os
import shutil
import tempfile
from pathlib import Path
import pytest
from unittest.mock import patch, MagicMock

from app import create_app
from app.config import TestingConfig
from app.extensions import db
from app.models.user import User


class IsolatedTestingConfig(TestingConfig):
    """Isolated testing configuration with a temporary upload folder."""
    TESTING = True
    DEBUG = False
    WTF_CSRF_ENABLED = False
    SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
    USING_SQLITE_FALLBACK = True
    SQLALCHEMY_ENGINE_OPTIONS = {}
    AI_SIMILARITY_THRESHOLD = 0.60


@pytest.fixture(scope="session")
def test_temp_dir():
    """Create a temporary directory for test uploads."""
    temp_dir = tempfile.mkdtemp(prefix="campusfind_test_")
    yield Path(temp_dir)
    shutil.rmtree(temp_dir, ignore_errors=True)


@pytest.fixture
def app(test_temp_dir):
    """Application fixture configured for isolated in-memory testing."""
    # Custom config overriding upload folder
    class ConfigWithTempUpload(IsolatedTestingConfig):
        UPLOAD_FOLDER = str(test_temp_dir)

    test_app = create_app(config_class=ConfigWithTempUpload)

    with test_app.app_context():
        db.create_all()
        yield test_app
        db.session.remove()
        db.drop_all()


@pytest.fixture
def client(app):
    """Flask test client."""
    return app.test_client()


@pytest.fixture
def runner(app):
    """Flask CLI test runner."""
    return app.test_cli_runner()


@pytest.fixture
def auth_student(app, client):
    """Create and authenticate a test student account."""
    with app.app_context():
        user = User(
            username="test_student",
            email="test.student@campus.edu",
            full_name="Test Student",
            phone="555-0100",
            role="student",
        )
        user.set_password("validPassword123")
        db.session.add(user)
        db.session.commit()
        user_id = user.id

    # Log in through test client
    client.post(
        "/login",
        data={"username_or_email": "test_student", "password": "validPassword123"},
        follow_redirects=True,
    )
    return user_id


@pytest.fixture
def auth_admin(app, client):
    """Create and authenticate a test administrator account."""
    with app.app_context():
        admin = User(
            username="test_admin",
            email="test.admin@campus.edu",
            full_name="Test Admin",
            phone="555-0200",
            role="admin",
        )
        admin.set_password("adminSecret123")
        db.session.add(admin)
        db.session.commit()
        admin_id = admin.id

    client.post(
        "/login",
        data={"username_or_email": "test_admin", "password": "adminSecret123"},
        follow_redirects=True,
    )
    return admin_id


@pytest.fixture
def mock_openclip():
    """Mock OpenCLIP AI service to avoid loading heavy models during unit tests."""
    with patch("app.services.ai_service.ai_service") as mock_ai:
        mock_ai.is_available.return_value = True
        mock_ai.get_status.return_value = {
            "available": True,
            "model_name": "ViT-B-32-Mock",
            "pretrained": "mock",
            "device": "cpu",
            "message": "Mock OpenCLIP service active.",
        }
        # Default mock embedding: normalized 512-dim vector of 0.04419
        import math
        mock_vec = [1.0 / math.sqrt(512)] * 512
        mock_ai.generate_embedding.return_value = mock_vec
        yield mock_ai
