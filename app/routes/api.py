import base64
import io
from pathlib import Path
from typing import List, Dict, Any
from flask import Blueprint, request, jsonify, current_app
from PIL import Image, UnidentifiedImageError
from app.services.ai_service import ai_service

api_bp = Blueprint("api", __name__)


@api_bp.route("/health", methods=["GET"])
def ai_health():
    """Diagnostic health endpoint reporting OpenCLIP model status."""
    status = ai_service.get_status()
    is_healthy = status.get("available", False)
    
    return jsonify({
        "status": "UP" if is_healthy else "DEGRADED",
        "service": "campusfind-ai",
        "ai": status
    }), (200 if is_healthy else 503)


@api_bp.route("/embeddings", methods=["POST"])
def generate_embedding():
    """
    Generate normalized 512-dimensional OpenCLIP embedding.
    Accepts:
    1. Multipart/form-data with file field 'image' or 'file'
    2. JSON payload with 'image_base64' string
    3. JSON payload with 'image_path' relative to upload folder
    """
    if not ai_service.is_available():
        return jsonify({
            "error": "AI service unavailable",
            "message": ai_service.get_status().get("message", "OpenCLIP model is not initialized.")
        }), 503

    image = None

    # Option 1: File upload via multipart/form-data
    if "image" in request.files or "file" in request.files:
        file_obj = request.files.get("image") or request.files.get("file")
        if not file_obj or not file_obj.filename:
            return jsonify({"error": "Bad Request", "message": "Uploaded file is empty or missing filename."}), 400
        try:
            image = Image.open(file_obj.stream).convert("RGB")
        except (UnidentifiedImageError, OSError, Exception) as exc:
            return jsonify({"error": "Bad Request", "message": f"Invalid or corrupt image content: {str(exc)}"}), 400

    # Option 2: JSON payload
    elif request.is_json:
        data = request.get_json(silent=True) or {}
        
        # Base64 string
        if "image_base64" in data and data["image_base64"]:
            try:
                b64_str = data["image_base64"]
                if "," in b64_str:
                    b64_str = b64_str.split(",", 1)[1]
                raw_bytes = base64.b64decode(b64_str)
                image = Image.open(io.BytesIO(raw_bytes)).convert("RGB")
            except Exception as exc:
                return jsonify({"error": "Bad Request", "message": f"Malformed base64 image: {str(exc)}"}), 400

        # Filename on server disk (within upload directory)
        elif "image_path" in data and data["image_path"]:
            upload_dir = Path(current_app.config.get("UPLOAD_FOLDER", "uploads"))
            target_path = upload_dir / Path(data["image_path"]).name
            if not target_path.is_file():
                return jsonify({"error": "Not Found", "message": f"Image not found at path: {target_path.name}"}), 404
            try:
                image = Image.open(target_path).convert("RGB")
            except Exception as exc:
                return jsonify({"error": "Bad Request", "message": f"Failed to read image: {str(exc)}"}), 400

    if image is None:
        return jsonify({
            "error": "Bad Request",
            "message": "No valid image provided. Supply 'image' multipart file or 'image_base64' in JSON."
        }), 400

    embedding = ai_service.generate_embedding(image)
    if embedding is None:
        return jsonify({
            "error": "Inference Error",
            "message": "OpenCLIP failed to generate embedding for the supplied image."
        }), 500

    return jsonify({
        "dimension": len(embedding),
        "embedding": embedding
    }), 200


@api_bp.route("/similarity", methods=["POST"])
def calculate_similarity():
    """
    Compute cosine similarity between two float vectors.
    Payload: { "embedding1": [...], "embedding2": [...] } or { "vector1": [...], "vector2": [...] }
    """
    if not request.is_json:
        return jsonify({"error": "Bad Request", "message": "Expected JSON payload."}), 400

    data = request.get_json(silent=True) or {}
    vec1 = data.get("embedding1") or data.get("vector1")
    vec2 = data.get("embedding2") or data.get("vector2")

    if not isinstance(vec1, list) or not isinstance(vec2, list):
        return jsonify({"error": "Bad Request", "message": "Both embedding vectors must be non-empty JSON arrays."}), 400

    if len(vec1) != len(vec2):
        return jsonify({
            "error": "Bad Request",
            "message": f"Vector dimension mismatch ({len(vec1)} vs {len(vec2)})."
        }), 400

    sim = ai_service.calculate_cosine_similarity(vec1, vec2)
    percentage = round(sim * 100.0, 1)

    return jsonify({
        "similarity": round(sim, 4),
        "percentage": percentage
    }), 200


@api_bp.route("/rank", methods=["POST"])
def rank_candidates():
    """
    Rank candidate embeddings against a target embedding using cosine similarity.
    Payload:
    {
      "target_embedding": [...],
      "candidates": [
        { "id": 101, "embedding": [...] },
        ...
      ],
      "threshold": 0.60,
      "limit": 6
    }
    """
    if not request.is_json:
        return jsonify({"error": "Bad Request", "message": "Expected JSON payload."}), 400

    data = request.get_json(silent=True) or {}
    target = data.get("target_embedding")
    candidates = data.get("candidates", [])
    threshold = float(data.get("threshold", 0.60))
    limit = int(data.get("limit", 6))

    if not isinstance(target, list) or not target:
        return jsonify({"error": "Bad Request", "message": "Missing or invalid 'target_embedding'."}), 400

    if not isinstance(candidates, list):
        return jsonify({"error": "Bad Request", "message": "'candidates' must be a list."}), 400

    matches = []
    for cand in candidates:
        cand_id = cand.get("id")
        cand_vec = cand.get("embedding")
        if cand_id is None or not isinstance(cand_vec, list) or len(cand_vec) != len(target):
            continue

        sim = ai_service.calculate_cosine_similarity(target, cand_vec)
        if sim >= threshold:
            matches.append({
                "id": cand_id,
                "similarity": round(sim, 4),
                "percentage": round(sim * 100.0, 1)
            })

    # Sort descending by similarity
    matches.sort(key=lambda m: m["similarity"], reverse=True)
    return jsonify({
        "count": len(matches[:limit]),
        "matches": matches[:limit]
    }), 200
