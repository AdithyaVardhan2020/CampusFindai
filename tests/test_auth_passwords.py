"""
Tests for Registration, Login, and Password Hashing.
Verifies secure cryptographic hashing, duplicate constraints, session login, and logout.
"""

from app.extensions import db
from app.models.user import User


def test_password_hashing_mechanism(app):
    """Verify passwords are cryptographically hashed using salted Werkzeug algorithms."""
    with app.app_context():
        user = User(username="hash_test", email="hash@campus.edu", full_name="Hash Tester")
        user.set_password("mySecurePassword123")

        # Plaintext password must never equal the stored hash
        assert user.password_hash != "mySecurePassword123"
        # Salted hash format check (e.g. scrypt: or pbkdf2:)
        assert user.password_hash.startswith(("scrypt:", "pbkdf2:"))
        assert len(user.password_hash) > 50

        # Password verification check
        assert user.check_password("mySecurePassword123") is True
        assert user.check_password("WrongPassword") is False
        assert user.check_password("") is False


def test_password_length_validation(app):
    """Verify passwords under 6 characters are rejected with ValueError."""
    with app.app_context():
        user = User(username="short_test", email="short@campus.edu", full_name="Short")
        import pytest
        with pytest.raises(ValueError):
            user.set_password("12345")


def test_user_registration_success(client, app):
    """Verify new user registration persists to the database with active status."""
    payload = {
        "full_name": "New Student",
        "username": "newstudent",
        "email": "new.student@campus.edu",
        "phone": "+1 555-0199",
        "password": "validPassword123",
        "confirm_password": "validPassword123",
    }
    response = client.post("/register", data=payload, follow_redirects=True)
    assert response.status_code == 200

    with app.app_context():
        user = User.query.filter_by(username="newstudent").first()
        assert user is not None
        assert user.email == "new.student@campus.edu"
        assert user.role == "student"
        assert user.is_active is True
        assert user.check_password("validPassword123") is True


def test_registration_duplicate_username_rejected(client, app):
    """Verify registration fails when username already exists."""
    with app.app_context():
        existing = User(username="duplicate_user", email="original@campus.edu", full_name="Original")
        existing.set_password("pass123")
        db.session.add(existing)
        db.session.commit()

    payload = {
        "full_name": "Duplicate Student",
        "username": "duplicate_user",
        "email": "different@campus.edu",
        "password": "validPassword123",
        "confirm_password": "validPassword123",
    }
    response = client.post("/register", data=payload)
    html = response.get_data(as_text=True)
    assert "already taken" in html.lower()


def test_registration_duplicate_email_rejected(client, app):
    """Verify registration fails when email address already exists."""
    with app.app_context():
        existing = User(username="user1", email="duplicate@campus.edu", full_name="User One")
        existing.set_password("pass123")
        db.session.add(existing)
        db.session.commit()

    payload = {
        "full_name": "Another Student",
        "username": "unique_username",
        "email": "duplicate@campus.edu",
        "password": "validPassword123",
        "confirm_password": "validPassword123",
    }
    response = client.post("/register", data=payload)
    html = response.get_data(as_text=True)
    assert "already exists" in html.lower()


def test_login_success(client, app):
    """Verify student can sign in with valid credentials."""
    with app.app_context():
        user = User(username="active_user", email="active@campus.edu", full_name="Active User")
        user.set_password("correctPassword")
        db.session.add(user)
        db.session.commit()

    response = client.post(
        "/login",
        data={"username_or_email": "active_user", "password": "correctPassword"},
        follow_redirects=True,
    )
    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "Active User" in html


def test_login_with_email(client, app):
    """Verify user can authenticate using email address instead of username."""
    with app.app_context():
        user = User(username="email_login_user", email="login_by_email@campus.edu", full_name="Email Login")
        user.set_password("correctPassword")
        db.session.add(user)
        db.session.commit()

    response = client.post(
        "/login",
        data={"username_or_email": "login_by_email@campus.edu", "password": "correctPassword"},
        follow_redirects=True,
    )
    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "Email Login" in html


def test_login_invalid_credentials(client, app):
    """Verify login failure on bad password."""
    with app.app_context():
        user = User(username="user_fail", email="fail@campus.edu", full_name="Fail User")
        user.set_password("correctPassword")
        db.session.add(user)
        db.session.commit()

    response = client.post(
        "/login",
        data={"username_or_email": "user_fail", "password": "wrongPassword"},
    )
    html = response.get_data(as_text=True)
    assert "Invalid username/email or password" in html


def test_logout(client, auth_student):
    """Verify session termination upon logout."""
    response = client.get("/logout", follow_redirects=True)
    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "You have been signed out." in html
