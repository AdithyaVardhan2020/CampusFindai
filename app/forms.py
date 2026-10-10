from datetime import date
from flask_wtf import FlaskForm
from flask_wtf.file import FileField, FileAllowed
from wtforms import StringField, PasswordField, BooleanField, TextAreaField, SelectField, DateField, SubmitField
from wtforms.validators import DataRequired, Email, Length, EqualTo, ValidationError, Optional
from app.models.user import User
from app.models.report import REPORT_CATEGORIES, REPORT_TYPES, REPORT_STATUSES


class RegistrationForm(FlaskForm):
    """User registration form for college students and staff."""
    username = StringField(
        "Username",
        validators=[
            DataRequired(message="Username is required."),
            Length(min=3, max=64, message="Username must be between 3 and 64 characters."),
        ],
    )
    email = StringField(
        "Campus Email",
        validators=[
            DataRequired(message="Campus email address is required."),
            Email(message="Please provide a valid email address."),
            Length(max=120),
        ],
    )
    full_name = StringField(
        "Full Name",
        validators=[
            DataRequired(message="Full name is required."),
            Length(min=2, max=100),
        ],
    )
    phone = StringField(
        "Phone Number (Optional)",
        validators=[Length(max=25)],
    )
    password = PasswordField(
        "Password",
        validators=[
            DataRequired(message="Password is required."),
            Length(min=6, max=128, message="Password must be at least 6 characters long."),
        ],
    )
    confirm_password = PasswordField(
        "Confirm Password",
        validators=[
            DataRequired(message="Please confirm your password."),
            EqualTo("password", message="Passwords must match."),
        ],
    )
    submit = SubmitField("Create Account")

    def validate_username(self, username):
        user = User.query.filter(User.username.ilike(username.data.strip())).first()
        if user:
            raise ValidationError("This username is already taken. Please choose another.")

    def validate_email(self, email):
        user = User.query.filter(User.email.ilike(email.data.strip())).first()
        if user:
            raise ValidationError("An account with this email address already exists.")


class LoginForm(FlaskForm):
    """Sign-in form accepting username or email."""
    username_or_email = StringField(
        "Username or Email",
        validators=[DataRequired(message="Please enter your username or email.")],
    )
    password = PasswordField(
        "Password",
        validators=[DataRequired(message="Please enter your password.")],
    )
    remember_me = BooleanField("Remember Me")
    submit = SubmitField("Sign In")


class ReportForm(FlaskForm):
    """Form for reporting a lost or found campus item."""
    title = StringField(
        "Item Title",
        validators=[
            DataRequired(message="Please enter a concise title for the item."),
            Length(min=3, max=120, message="Title must be between 3 and 120 characters."),
        ],
    )
    report_type = SelectField(
        "Report Type",
        choices=[("lost", "Lost — I lost an item"), ("found", "Found — I found an item")],
        validators=[DataRequired()],
    )
    category = SelectField(
        "Category",
        choices=[(c, c) for c in REPORT_CATEGORIES],
        validators=[DataRequired()],
    )
    description = TextAreaField(
        "Detailed Description",
        validators=[
            DataRequired(message="Please describe the item (brand, color, distinctive marks)."),
            Length(min=10, max=2000, message="Description must be between 10 and 2000 characters."),
        ],
    )
    date_incident = DateField(
        "Date Lost / Found",
        default=date.today,
        validators=[DataRequired(message="Please provide the date.")],
    )
    location = StringField(
        "Campus Location",
        validators=[
            DataRequired(message="Location where the item was lost or found is required."),
            Length(min=2, max=150),
        ],
    )
    contact_info = StringField(
        "Contact Information",
        validators=[
            DataRequired(message="Provide an email, phone number, or campus office location."),
            Length(min=3, max=150),
        ],
    )
    image = FileField(
        "Item Photo (Optional, recommended for AI matching)",
        validators=[
            FileAllowed(["png", "jpg", "jpeg", "webp"], "Only PNG, JPG, JPEG, and WEBP image formats are supported."),
        ],
    )
    submit = SubmitField("Submit Report")


class ReportEditForm(ReportForm):
    """Form for updating an existing report, including status changes."""
    status = SelectField(
        "Status",
        choices=[("open", "Open (Active)"), ("resolved", "Resolved (Returned to Owner)"), ("closed", "Closed")],
        validators=[DataRequired()],
    )
    submit = SubmitField("Update Report")
