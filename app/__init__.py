import os
import logging
from pathlib import Path
from flask import Flask, render_template
from app.config import config_by_name, Config
from app.extensions import db, login_manager, csrf
from app.services.ai_service import ai_service


def create_app(config_name: str = "default", config_class=None) -> Flask:
    """
    Application Factory for CampusFind.
    Initializes Flask application, database connection, authentication, 
    blueprints, and OpenCLIP AI matching engine.
    """
    app = Flask(__name__)

    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] %(levelname)s in %(module)s: %(message)s",
    )

    # Load configuration
    if config_class:
        app.config.from_object(config_class)
    else:
        app.config.from_object(config_by_name.get(config_name, Config))

    # Ensure upload directory exists
    upload_dir = Path(app.config["UPLOAD_FOLDER"])
    upload_dir.mkdir(parents=True, exist_ok=True)

    # Initialize extensions
    db.init_app(app)
    login_manager.init_app(app)
    csrf.init_app(app)

    # Configure AI Service parameters from app config
    ai_service.model_name = app.config.get("AI_MODEL_NAME", "ViT-B-32")
    ai_service.pretrained = app.config.get("AI_PRETRAINED_SOURCE", "openai")

    # Register Blueprints
    from app.routes import main_bp, auth_bp, reports_bp, dashboard_bp, admin_bp, api_bp
    app.register_blueprint(main_bp)
    app.register_blueprint(auth_bp)
    app.register_blueprint(reports_bp)
    app.register_blueprint(dashboard_bp)
    app.register_blueprint(admin_bp)

    # Register JSON REST API blueprint with CSRF exemption for Java backend integration
    csrf.exempt(api_bp)
    app.register_blueprint(api_bp, url_prefix="/api/v1/ai")

    # Register Error Handlers
    register_error_handlers(app)

    # Register Context Processors & Filters
    register_template_helpers(app)

    # Initialize tables if running in development mode with SQLite fallback
    with app.app_context():
        if app.config.get("USING_SQLITE_FALLBACK", False) and not app.config.get("TESTING", False):
            try:
                db.create_all()
                app.logger.info("Local SQLite database initialized at: %s", app.config["SQLALCHEMY_DATABASE_URI"])
            except Exception as exc:
                app.logger.error("Failed to auto-create SQLite tables: %s", exc)

    return app


def register_error_handlers(app: Flask) -> None:
    """Custom error pages for friendly student navigation."""

    @app.errorhandler(403)
    def forbidden(error):
        return render_template("errors/403.html"), 403

    @app.errorhandler(404)
    def page_not_found(error):
        return render_template("errors/404.html"), 404

    @app.errorhandler(500)
    def internal_server_error(error):
        return render_template("errors/500.html"), 500


def register_template_helpers(app: Flask) -> None:
    """Inject helpful Jinja filters and global variables."""

    @app.template_filter("format_date")
    def format_date(value, fmt="%b %d, %Y"):
        if not value:
            return ""
        return value.strftime(fmt)

    @app.template_filter("category_badge_class")
    def category_badge_class(category: str) -> str:
        mapping = {
            "Phones": "bg-primary",
            "Laptops": "bg-indigo text-white",
            "Electronics": "bg-info text-dark",
            "Wallets": "bg-success",
            "Keys": "bg-warning text-dark",
            "ID Cards": "bg-danger",
            "Bags": "bg-secondary",
            "Books": "bg-dark",
            "Clothing": "bg-teal text-white",
            "Accessories": "bg-purple text-white",
            "Other": "bg-light text-dark border",
        }
        return mapping.get(category, "bg-secondary")

    @app.context_processor
    def inject_global_vars():
        return {
            "app_name": "CampusFind",
            "college_name": "University Campus Lost & Found System",
            "ai_model_name": app.config.get("AI_MODEL_NAME", "ViT-B-32"),
        }
