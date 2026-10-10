import os
import uuid
from pathlib import Path
from typing import Optional, Tuple
from PIL import Image, UnidentifiedImageError
from werkzeug.datastructures import FileStorage


class ImageValidationError(Exception):
    """Custom exception raised when an uploaded image fails validation."""
    pass


ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "webp"}
MAX_DIMENSION = 1920  # Max width or height in pixels


def is_allowed_extension(filename: str) -> bool:
    """Check if filename has a permitted file extension."""
    if not filename or "." not in filename:
        return False
    ext = filename.rsplit(".", 1)[1].lower()
    return ext in ALLOWED_EXTENSIONS


def get_file_extension(filename: str) -> str:
    """Return lowercase extension including leading dot, e.g. '.jpg'."""
    if "." in filename:
        return "." + filename.rsplit(".", 1)[1].lower()
    return ".jpg"


def validate_and_save_image(
    file_storage: FileStorage,
    upload_folder: str,
    max_size_bytes: int = 16 * 1024 * 1024
) -> str:
    """
    Validate, sanitize, resize, and persist an uploaded image.
    
    Returns:
        The safe unique filename (e.g. '3b2d1...c9.jpg').
    
    Raises:
        ImageValidationError: If the file is missing, exceeds size, has an
                              invalid extension, or contains corrupt image data.
    """
    if not file_storage or not file_storage.filename:
        raise ImageValidationError("No image file provided.")

    original_filename = file_storage.filename.strip()
    if not is_allowed_extension(original_filename):
        allowed = ", ".join(sorted(ALLOWED_EXTENSIONS))
        raise ImageValidationError(f"Invalid file type. Permitted formats: {allowed}")

    # Ensure upload directory exists
    target_dir = Path(upload_folder)
    target_dir.mkdir(parents=True, exist_ok=True)

    # Inspect stream with Pillow
    file_storage.seek(0, os.SEEK_END)
    size_bytes = file_storage.tell()
    file_storage.seek(0)

    if size_bytes > max_size_bytes:
        mb_limit = max_size_bytes // (1024 * 1024)
        raise ImageValidationError(f"File size exceeds maximum allowed limit of {mb_limit}MB.")

    if size_bytes == 0:
        raise ImageValidationError("Uploaded image file is empty.")

    try:
        image = Image.open(file_storage.stream)
        image.verify()  # Fast check for corruption
        
        # Re-open for actual processing (verify closes stream in PIL)
        file_storage.seek(0)
        image = Image.open(file_storage.stream)
    except (UnidentifiedImageError, OSError, Exception) as exc:
        raise ImageValidationError(f"Invalid or corrupt image content: {str(exc)}")

    # Standardize image color space to RGB
    if image.mode in ("RGBA", "LA", "P"):
        rgb_image = Image.new("RGB", image.size, (255, 255, 255))
        if image.mode == "P":
            image = image.convert("RGBA")
        rgb_image.paste(image, mask=image.split()[-1] if "A" in image.mode else None)
        image = rgb_image
    elif image.mode != "RGB":
        image = image.convert("RGB")

    # Resize if unusually large to save disk space and accelerate OpenCLIP inference
    width, height = image.size
    if width > MAX_DIMENSION or height > MAX_DIMENSION:
        image.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)

    # Generate a cryptographically secure random filename
    unique_name = f"{uuid.uuid4().hex}.jpg"
    destination_path = target_dir / unique_name

    try:
        # Save as optimized JPEG
        image.save(destination_path, format="JPEG", quality=88, optimize=True)
    except Exception as exc:
        raise ImageValidationError(f"Failed to save image on server: {str(exc)}")

    return unique_name


def delete_image_file(filename: Optional[str], upload_folder: str) -> bool:
    """Safely delete an image file from the uploads directory."""
    if not filename:
        return False
    try:
        safe_name = os.path.basename(filename)
        path = Path(upload_folder) / safe_name
        if path.is_file():
            path.unlink()
            return True
    except OSError:
        pass
    return False


def get_image_file_path(filename: Optional[str], upload_folder: str) -> Optional[str]:
    """Return the absolute path of an image if it exists on disk, else None."""
    if not filename:
        return None
    safe_name = os.path.basename(filename)
    path = Path(upload_folder) / safe_name
    if path.is_file():
        return str(path)
    return None
