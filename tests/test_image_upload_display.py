"""
Tests for Image Upload and Display.
Verifies image sanitization, format conversion, file persistence, and serving behavior.
"""

import io
import os
from pathlib import Path
from PIL import Image
import pytest

from app.extensions import db
from app.models.report import Report
from app.services.image_service import (
    validate_and_save_image,
    delete_image_file,
    ImageValidationError,
)
from werkzeug.datastructures import FileStorage


def create_test_image_filestorage(filename="test.png", color=(100, 150, 200), size=(200, 200)):
    """Helper creating an in-memory image FileStorage object."""
    stream = io.BytesIO()
    img = Image.new("RGB", size, color=color)
    img.save(stream, format="PNG")
    stream.seek(0)
    return FileStorage(stream=stream, filename=filename, content_type="image/png")


def test_validate_and_save_image_success(test_temp_dir):
    """Verify image upload is converted to optimized JPEG and saved with unique UUID filename."""
    file_storage = create_test_image_filestorage()
    saved_name = validate_and_save_image(file_storage, str(test_temp_dir))

    assert saved_name.endswith(".jpg")
    file_path = test_temp_dir / saved_name
    assert file_path.is_file()
    assert file_path.stat().st_size > 0

    # Verify output can be read as JPEG
    with Image.open(file_path) as saved_img:
        assert saved_img.format == "JPEG"


def test_validate_and_save_image_rejects_invalid_extension(test_temp_dir):
    """Verify non-image files are rejected with ImageValidationError."""
    bad_file = FileStorage(
        stream=io.BytesIO(b"malicious script content"),
        filename="exploit.exe",
        content_type="application/octet-stream",
    )
    with pytest.raises(ImageValidationError) as exc_info:
        validate_and_save_image(bad_file, str(test_temp_dir))
    assert "Invalid file type" in str(exc_info.value)


def test_validate_and_save_image_rejects_corrupt_content(test_temp_dir):
    """Verify spoofed extension with corrupt binary content is rejected."""
    corrupt_file = FileStorage(
        stream=io.BytesIO(b"not a valid image format"),
        filename="fake_image.jpg",
        content_type="image/jpeg",
    )
    with pytest.raises(ImageValidationError) as exc_info:
        validate_and_save_image(corrupt_file, str(test_temp_dir))
    assert "Invalid or corrupt" in str(exc_info.value)


def test_delete_image_file_removes_from_disk(test_temp_dir):
    """Verify delete_image_file successfully unlinks file."""
    file_storage = create_test_image_filestorage()
    saved_name = validate_and_save_image(file_storage, str(test_temp_dir))
    assert (test_temp_dir / saved_name).is_file()

    deleted = delete_image_file(saved_name, str(test_temp_dir))
    assert deleted is True
    assert not (test_temp_dir / saved_name).exists()


def test_report_form_submission_with_image(client, auth_student, app, test_temp_dir, mock_openclip):
    """Verify submitting a report with an image attaches filename and saves to upload folder."""
    stream = io.BytesIO()
    Image.new("RGB", (150, 150), color=(255, 120, 0)).save(stream, format="JPEG")
    stream.seek(0)

    payload = {
        "title": "Orange Water Bottle",
        "report_type": "lost",
        "category": "Other",
        "description": "Bright orange steel water bottle",
        "date_incident": "2026-10-09",
        "location": "Fitness Center",
        "contact_info": "student@campus.edu",
        "image": (stream, "orange_bottle.jpg"),
    }
    response = client.post("/reports/new", data=payload, follow_redirects=True, content_type="multipart/form-data")
    assert response.status_code == 200

    with app.app_context():
        report = Report.query.filter_by(title="Orange Water Bottle").first()
        assert report is not None
        assert report.image_filename is not None
        assert (test_temp_dir / report.image_filename).is_file()


def test_image_display_route_serving(client, test_temp_dir):
    """Verify /uploads/<filename> serves uploaded images when UPLOAD_FOLDER is configured as absolute path."""
    file_storage = create_test_image_filestorage()
    saved_name = validate_and_save_image(file_storage, str(test_temp_dir))

    response = client.get(f"/uploads/{saved_name}")
    assert response.status_code == 200
    assert "image" in response.content_type


def test_image_display_route_fallback_on_missing(client):
    """Verify /uploads/<missing> returns placeholder SVG instead of crashing."""
    response = client.get("/uploads/completely_missing_file_xyz.jpg")
    assert response.status_code == 200
    assert "image/svg+xml" in response.content_type


def test_relative_upload_folder_path_behavior(app):
    """
    Audit check: Verify whether a relative UPLOAD_FOLDER path causes send_from_directory to fail.
    Documents the current application's behavior when UPLOAD_FOLDER='uploads' in .env.
    """
    from flask import send_from_directory
    from werkzeug.exceptions import NotFound

    with app.test_request_context("/uploads/test.jpg"):
        # When relative path 'uploads' is used directly with send_from_directory
        try:
            send_from_directory("uploads", "test.jpg")
            relative_result = "SUCCESS"
        except NotFound:
            relative_result = "NOT_FOUND_404"
        except Exception as exc:
            relative_result = f"ERROR_{type(exc).__name__}"

        # Current implementation fails with 404 because Flask resolves relative to app.root_path
        assert relative_result == "NOT_FOUND_404"
