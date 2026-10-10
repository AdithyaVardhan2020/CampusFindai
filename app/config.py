import os
from pathlib import Path
from dotenv import load_dotenv

# Base directory of the repository
BASE_DIR = Path(__file__).resolve().parent.parent

# Load environment variables from .env file
load_dotenv(BASE_DIR / '.env')


def sanitize_db_url(url: str) -> str:
    """Normalize database URL for PyMySQL compatibility."""
    if not url:
        return ""
    url = url.strip()
    # Aiven often provides mysql:// - SQLAlchemy with PyMySQL requires mysql+pymysql://
    if url.startswith("mysql://"):
        url = "mysql+pymysql://" + url[len("mysql://"):]
    return url


class Config:
    """Base application configuration."""

    SECRET_KEY = os.getenv("SECRET_KEY", "campusfind-dev-fallback-key-replace-in-production")
    WTF_CSRF_ENABLED = True

    # Directories
    BASE_DIR = BASE_DIR
    UPLOAD_FOLDER = os.getenv("UPLOAD_FOLDER", str(BASE_DIR / "uploads"))
    MAX_CONTENT_LENGTH = int(os.getenv("MAX_CONTENT_LENGTH_MB", "16")) * 1024 * 1024
    ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}

    # Database resolution (Aiven MySQL vs local SQLite fallback)
    _raw_db_url = os.getenv("DATABASE_URL", "").strip()
    _ca_cert_path = os.getenv("AIVEN_CA_CERT", "").strip()

    if _raw_db_url:
        SQLALCHEMY_DATABASE_URI = sanitize_db_url(_raw_db_url)
        USING_SQLITE_FALLBACK = False
    else:
        # Documented SQLite local fallback for development/evaluation without live Aiven credentials
        instance_dir = BASE_DIR / "instance"
        instance_dir.mkdir(parents=True, exist_ok=True)
        SQLALCHEMY_DATABASE_URI = f"sqlite:///{instance_dir / 'campusfind.db'}"
        USING_SQLITE_FALLBACK = True

    # SQLAlchemy Engine options (connection pooling, health checks, SSL)
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    SQLALCHEMY_ENGINE_OPTIONS = {
        "pool_recycle": 280,
        "pool_pre_ping": True,
    }

    # Attach SSL connection parameters if Aiven CA certificate is provided
    if _ca_cert_path and os.path.isfile(_ca_cert_path) and not USING_SQLITE_FALLBACK:
        SQLALCHEMY_ENGINE_OPTIONS["connect_args"] = {
            "ssl": {
                "ca": _ca_cert_path,
                "check_hostname": True,
            }
        }

    # AI Configuration (OpenCLIP)
    AI_MODEL_NAME = os.getenv("AI_MODEL_NAME", "ViT-B-32")
    AI_PRETRAINED_SOURCE = os.getenv("AI_PRETRAINED_SOURCE", "openai")
    AI_SIMILARITY_THRESHOLD = float(os.getenv("AI_SIMILARITY_THRESHOLD", "0.60"))
    AI_MAX_MATCHES = int(os.getenv("AI_MAX_MATCHES", "6"))

    # Security & Cookies
    SESSION_COOKIE_HTTPONLY = True
    SESSION_COOKIE_SAMESITE = os.getenv("SESSION_COOKIE_SAMESITE", "Lax")
    SESSION_COOKIE_SECURE = os.getenv("SESSION_COOKIE_SECURE", "False").lower() in ("true", "1")


class DevelopmentConfig(Config):
    """Development configuration."""
    DEBUG = True


class TestingConfig(Config):
    """Isolated testing configuration (in-memory SQLite, CSRF disabled)."""
    TESTING = True
    DEBUG = False
    WTF_CSRF_ENABLED = False
    SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
    USING_SQLITE_FALLBACK = True
    SQLALCHEMY_ENGINE_OPTIONS = {}
    AI_SIMILARITY_THRESHOLD = 0.60


class ProductionConfig(Config):
    """Production configuration enforcing strict security."""
    DEBUG = False
    SESSION_COOKIE_SECURE = True

    @classmethod
    def validate_production(cls):
        """Validates that production environment has required secrets and configurations."""
        secret = os.getenv("SECRET_KEY")
        if not secret or secret == "campusfind-dev-fallback-key-replace-in-production":
            raise ValueError("Production requires a strong, random SECRET_KEY environment variable.")
        if cls.USING_SQLITE_FALLBACK:
            raise ValueError("Production requires a valid DATABASE_URL (Aiven MySQL) connection string.")


config_by_name = {
    "development": DevelopmentConfig,
    "testing": TestingConfig,
    "production": ProductionConfig,
    "default": DevelopmentConfig,
}
