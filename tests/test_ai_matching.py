"""
Tests for OpenCLIP Image Embedding Generation and Cosine Similarity Matching.
Includes fast unit tests for similarity mathematics and matching algorithms,
plus a dedicated verification test checking whether the real OpenCLIP model can load.
"""

import math
from PIL import Image
import pytest
from app.models.report import Report
from app.services.ai_service import OpenCLIPService, ai_service


# -----------------------------------------------------------------------------
# Unit Tests: Cosine Similarity Mathematics
# -----------------------------------------------------------------------------

def test_cosine_similarity_identical_vectors():
    """Verify identical vectors yield exactly 1.0 cosine similarity."""
    vec1 = [1.0, 2.0, 3.0]
    vec2 = [1.0, 2.0, 3.0]
    sim = OpenCLIPService.calculate_cosine_similarity(vec1, vec2)
    assert pytest.approx(sim, abs=1e-5) == 1.0


def test_cosine_similarity_orthogonal_vectors():
    """Verify orthogonal (perpendicular) vectors yield 0.0 cosine similarity."""
    vec1 = [1.0, 0.0]
    vec2 = [0.0, 1.0]
    sim = OpenCLIPService.calculate_cosine_similarity(vec1, vec2)
    assert pytest.approx(sim, abs=1e-5) == 0.0


def test_cosine_similarity_opposite_vectors():
    """Verify diametrically opposite vectors yield -1.0 cosine similarity."""
    vec1 = [1.0, 1.0, 1.0]
    vec2 = [-1.0, -1.0, -1.0]
    sim = OpenCLIPService.calculate_cosine_similarity(vec1, vec2)
    assert pytest.approx(sim, abs=1e-5) == -1.0


def test_cosine_similarity_handles_zero_vectors_gracefully():
    """Verify zero vectors do not cause division by zero and return 0.0."""
    vec1 = [0.0, 0.0, 0.0]
    vec2 = [1.0, 2.0, 3.0]
    assert OpenCLIPService.calculate_cosine_similarity(vec1, vec2) == 0.0
    assert OpenCLIPService.calculate_cosine_similarity([], vec2) == 0.0


def test_cosine_similarity_dimension_mismatch():
    """Verify vector length mismatches are handled gracefully returning 0.0."""
    vec1 = [1.0, 2.0]
    vec2 = [1.0, 2.0, 3.0]
    assert OpenCLIPService.calculate_cosine_similarity(vec1, vec2) == 0.0


# -----------------------------------------------------------------------------
# Unit Tests: Potential Matches Algorithm & Rules
# -----------------------------------------------------------------------------

def test_find_potential_matches_ranking_and_threshold():
    """Verify potential matches ranks candidates above threshold in descending order."""
    service = OpenCLIPService()

    # Target: Lost report
    target = Report(id=1, title="Lost Target", report_type="lost", status="open")
    target_vec = [1.0, 0.0, 0.0]
    target.set_embedding(target_vec)

    # Candidate 1: High similarity found report (sim ~ 0.95)
    c1 = Report(id=2, title="Found High Match", report_type="found", status="open")
    c1.set_embedding([0.95, 0.31, 0.0])

    # Candidate 2: Medium similarity found report (sim ~ 0.70)
    c2 = Report(id=3, title="Found Medium Match", report_type="found", status="open")
    c2.set_embedding([0.70, 0.71, 0.0])

    # Candidate 3: Low similarity found report (sim ~ 0.30 - below 0.60 threshold)
    c3 = Report(id=4, title="Found Low Match", report_type="found", status="open")
    c3.set_embedding([0.30, 0.95, 0.0])

    candidates = [c3, c1, c2]  # Unordered input
    matches = service.find_potential_matches(target, candidates, threshold=0.60, limit=5)

    assert len(matches) == 2  # c3 excluded because < 0.60
    assert matches[0]["report"].id == 2  # Highest similarity first
    assert matches[1]["report"].id == 3
    assert matches[0]["percentage"] > matches[1]["percentage"]


def test_find_potential_matches_excludes_same_report_type():
    """Verify lost items never match against other lost items, and found against found."""
    service = OpenCLIPService()

    target = Report(id=1, title="Lost Item", report_type="lost", status="open")
    target.set_embedding([1.0, 0.0])

    # Same report type with identical vector
    candidate_same_type = Report(id=2, title="Another Lost Item", report_type="lost", status="open")
    candidate_same_type.set_embedding([1.0, 0.0])

    matches = service.find_potential_matches(target, [candidate_same_type], threshold=0.50)
    assert len(matches) == 0


def test_find_potential_matches_excludes_self():
    """Verify target report is never returned as its own match."""
    service = OpenCLIPService()

    target = Report(id=1, title="Item", report_type="lost", status="open")
    target.set_embedding([1.0, 0.0])

    matches = service.find_potential_matches(target, [target], threshold=0.10)
    assert len(matches) == 0


# -----------------------------------------------------------------------------
# Integration Test: Real OpenCLIP Neural Model Loading
# -----------------------------------------------------------------------------

def test_real_openclip_model_can_load_and_infer():
    """
    Dedicated Verification Check:
    Verify whether the real OpenCLIP ViT-B-32 model can successfully load into memory
    and perform forward pass inference to produce 512-dimension normalized embeddings.
    """
    # Use real global service
    status = ai_service.get_status()
    # If not yet initialized, initialize
    if not status["available"]:
        initialized = ai_service.initialize()
        status = ai_service.get_status()

    assert status["available"] is True, f"OpenCLIP model failed to load: {status['message']}"
    assert status["model_name"] == "ViT-B-32"

    # Test real forward pass on an actual PIL image
    test_img = Image.new("RGB", (224, 224), color=(30, 90, 200))
    embedding = ai_service.generate_embedding(test_img)

    assert embedding is not None, "Failed to generate embedding from real OpenCLIP model"
    assert isinstance(embedding, list)
    assert len(embedding) == 512

    # Verify L2 normalization: sum of squares should approximate 1.0
    l2_norm = math.sqrt(sum(x * x for x in embedding))
    assert pytest.approx(l2_norm, abs=1e-3) == 1.0
