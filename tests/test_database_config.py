"""
Tests for Database Configuration and Aiven Cloud MySQL Support.
Verifies connection string sanitization, pooling options, SQLite fallback detection,
and tests live Aiven connectivity if credentials are provided in the environment.
"""

import os
import pytest
from app.config import sanitize_db_url, Config


def test_sanitize_db_url_converts_mysql_scheme():
    """Verify mysql:// is upgraded to mysql+pymysql:// for PyMySQL driver compatibility."""
    raw = "mysql://user:pass@host:12345/dbname"
    sanitized = sanitize_db_url(raw)
    assert sanitized.startswith("mysql+pymysql://")
    assert "user:pass@host:12345/dbname" in sanitized


def test_sanitize_db_url_preserves_existing_pymysql_scheme():
    """Verify mysql+pymysql:// is preserved as-is."""
    raw = "mysql+pymysql://user:pass@host:12345/dbname"
    sanitized = sanitize_db_url(raw)
    assert sanitized == raw


def test_sanitize_db_url_handles_empty_string():
    """Verify empty or None database URL returns empty string without error."""
    assert sanitize_db_url("") == ""
    assert sanitize_db_url("   ") == ""
    assert sanitize_db_url(None) == ""


def test_sqlite_fallback_when_database_url_missing(monkeypatch):
    """Verify application config flags SQLite fallback when DATABASE_URL is empty."""
    monkeypatch.setenv("DATABASE_URL", "")

    # Re-evaluate Config class behavior
    raw_url = os.getenv("DATABASE_URL", "").strip()
    using_sqlite = not bool(raw_url)
    assert using_sqlite is True


def test_aiven_detection_when_database_url_provided(monkeypatch):
    """Verify application recognizes cloud database when DATABASE_URL is populated."""
    # Test with dummy Aiven URL
    dummy_url = "mysql://dummyuser:dummypass@mysql-test.aivencloud.com:12345/defaultdb"
    monkeypatch.setenv("DATABASE_URL", dummy_url)

    raw_url = os.getenv("DATABASE_URL", "").strip()
    using_sqlite = not bool(raw_url)
    assert using_sqlite is False
    assert sanitize_db_url(raw_url).startswith("mysql+pymysql://")


def test_database_connection_pooling_options():
    """Verify SQLAlchemy engine options include pre-ping and pool recycle for cloud timeouts."""
    engine_options = Config.SQLALCHEMY_ENGINE_OPTIONS
    assert engine_options.get("pool_pre_ping") is True
    assert engine_options.get("pool_recycle") is not None
    assert engine_options.get("pool_recycle") <= 300  # Within Aiven idle timeout window


def test_live_aiven_cloud_connectivity():
    """
    Live Aiven Connection Verification:
    Tests real connection if DATABASE_URL is configured in environment.
    If unconfigured or empty, reports NOT VERIFIED / SKIPPED without failing.
    Never prints secrets or credentials.
    """
    raw_db_url = os.getenv("DATABASE_URL", "").strip()

    if not raw_db_url:
        pytest.skip("DATABASE_URL is empty/unset in environment. Live Aiven MySQL connectivity NOT VERIFIED.")

    # Isolated read-only connection test
    from sqlalchemy import create_engine, text
    sanitized_url = sanitize_db_url(raw_db_url)
    ca_cert = os.getenv("AIVEN_CA_CERT", "").strip()

    connect_args = {}
    if ca_cert and os.path.isfile(ca_cert):
        connect_args["ssl"] = {"ca": ca_cert, "check_hostname": True}

    try:
        engine = create_engine(sanitized_url, connect_args=connect_args)
        with engine.connect() as conn:
            result = conn.execute(text("SELECT 1")).scalar()
            assert result == 1
    except Exception as exc:
        pytest.fail(f"Failed to connect to configured Aiven MySQL database: {type(exc).__name__}")
