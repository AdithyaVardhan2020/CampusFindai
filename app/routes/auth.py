from flask import Blueprint, render_template, redirect, url_for, flash, request
from flask_login import login_user, logout_user, login_required, current_user
from urllib.parse import urlsplit
from app.extensions import db
from app.models.user import User
from app.forms import RegistrationForm, LoginForm

auth_bp = Blueprint("auth", __name__)


def is_safe_redirect_url(target: str) -> bool:
    """Validate that the redirect target URL is local to prevent open-redirect attacks."""
    if not target:
        return False
    ref_url = urlsplit(request.host_url)
    test_url = urlsplit(target)
    return test_url.scheme in ("", "http", "https") and (test_url.netloc == "" or test_url.netloc == ref_url.netloc)


@auth_bp.route("/register", methods=["GET", "POST"])
@auth_bp.route("/auth/register", methods=["GET", "POST"])
def register():
    """Register a new student account."""
    if current_user.is_authenticated:
        return redirect(url_for("reports.index"))

    form = RegistrationForm()
    if form.validate_on_submit():
        user = User(
            username=form.username.data.strip(),
            email=form.email.data.strip().lower(),
            full_name=form.full_name.data.strip(),
            phone=form.phone.data.strip() if form.phone.data else None,
            role="student",
        )
        user.set_password(form.password.data)

        db.session.add(user)
        try:
            db.session.commit()
            flash("Account created successfully! You can now sign in.", "success")
            return redirect(url_for("auth.login"))
        except Exception as exc:
            db.session.rollback()
            flash(f"An error occurred while creating your account: {str(exc)}", "danger")

    return render_template("auth/register.html", form=form)


@auth_bp.route("/login", methods=["GET", "POST"])
@auth_bp.route("/auth/login", methods=["GET", "POST"])
def login():
    """Authenticate student or administrator credentials."""
    if current_user.is_authenticated:
        return redirect(url_for("reports.index"))

    form = LoginForm()
    if form.validate_on_submit():
        identifier = form.username_or_email.data.strip()
        # Find user by username or email
        user = User.query.filter(
            (User.username.ilike(identifier)) | (User.email.ilike(identifier))
        ).first()

        if user and user.check_password(form.password.data):
            if not user.is_active:
                flash("Your account has been deactivated. Please contact campus security.", "warning")
                return render_template("auth/login.html", form=form)

            login_user(user, remember=form.remember_me.data)
            flash(f"Welcome back, {user.full_name}!", "success")

            next_page = request.args.get("next")
            if next_page and is_safe_redirect_url(next_page):
                return redirect(next_page)
            return redirect(url_for("reports.index"))

        flash("Invalid username/email or password. Please try again.", "danger")

    return render_template("auth/login.html", form=form)


@auth_bp.route("/logout")
@auth_bp.route("/auth/logout")
@login_required
def logout():
    """Terminate user session."""
    logout_user()
    flash("You have been signed out.", "info")
    return redirect(url_for("main.index"))
