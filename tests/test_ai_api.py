import io
import pytest
from PIL import Image


def test_ai_health_endpoint(client):
    """Verify GET /api/v1/ai/health returns 200 or 503 with diagnostic metadata."""
    response = client.get("/api/v1/ai/health")
    assert response.status_code in (200, 503)
    data = response.get_json()
    assert "status" in data
    assert data["service"] == "campusfind-ai"
    assert "ai" in data
    assert "model_name" in data["ai"]


def test_ai_similarity_endpoint(client):
    """Verify POST /api/v1/ai/similarity computes accurate cosine similarity."""
    # Identical vectors should yield similarity = 1.0
    vec1 = [1.0, 0.0, 0.0, 0.0]
    vec2 = [1.0, 0.0, 0.0, 0.0]
    
    response = client.post("/api/v1/ai/similarity", json={
        "embedding1": vec1,
        "embedding2": vec2
    })
    assert response.status_code == 200
    data = response.get_json()
    assert data["similarity"] == 1.0
    assert data["percentage"] == 100.0

    # Orthogonal vectors should yield similarity = 0.0
    vec_ortho = [0.0, 1.0, 0.0, 0.0]
    response2 = client.post("/api/v1/ai/similarity", json={
        "embedding1": vec1,
        "embedding2": vec_ortho
    })
    assert response2.status_code == 200
    data2 = response2.get_json()
    assert data2["similarity"] == 0.0
    assert data2["percentage"] == 0.0


def test_ai_similarity_dimension_mismatch(client):
    """Verify similarity endpoint rejects mismatched vector dimensions with 400."""
    response = client.post("/api/v1/ai/similarity", json={
        "embedding1": [1.0, 2.0],
        "embedding2": [1.0, 2.0, 3.0]
    })
    assert response.status_code == 400
    data = response.get_json()
    assert "mismatch" in data["message"].lower()


def test_ai_rank_endpoint(client):
    """Verify POST /api/v1/ai/rank correctly ranks candidates in descending order."""
    target = [1.0, 0.0, 0.0]
    candidates = [
        {"id": 101, "embedding": [0.5, 0.5, 0.0]},  # Moderate similarity
        {"id": 102, "embedding": [0.99, 0.01, 0.0]}, # High similarity (~0.99)
        {"id": 103, "embedding": [0.0, 1.0, 0.0]}    # Orthogonal (~0.0, filtered out if threshold > 0)
    ]
    
    response = client.post("/api/v1/ai/rank", json={
        "target_embedding": target,
        "candidates": candidates,
        "threshold": 0.50,
        "limit": 5
    })
    assert response.status_code == 200
    data = response.get_json()
    assert data["count"] == 2
    # Candidate 102 must rank first
    assert data["matches"][0]["id"] == 102
    assert data["matches"][0]["similarity"] > 0.95
    assert data["matches"][1]["id"] == 101


def test_ai_embeddings_endpoint_with_image(client):
    """Verify POST /api/v1/ai/embeddings handles image upload."""
    # Create simple in-memory 50x50 RGB image
    img = Image.new("RGB", (50, 50), color="blue")
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)

    response = client.post(
        "/api/v1/ai/embeddings",
        data={"image": (buf, "test_item.jpg")},
        content_type="multipart/form-data"
    )
    # If OpenCLIP is available in this env, returns 200 with 512-dim embedding
    # If not loaded/no weights, returns 503
    assert response.status_code in (200, 503)
    if response.status_code == 200:
        data = response.get_json()
        assert "embedding" in data
        assert data["dimension"] == 512
        assert len(data["embedding"]) == 512


def test_ai_embeddings_endpoint_empty_file(client):
    """Verify POST /api/v1/ai/embeddings rejects missing or empty file with 400."""
    response = client.post(
        "/api/v1/ai/embeddings",
        data={},
        content_type="multipart/form-data"
    )
    assert response.status_code in (400, 503)
