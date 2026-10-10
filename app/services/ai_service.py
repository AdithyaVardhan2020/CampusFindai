import math
import logging
from pathlib import Path
from typing import List, Dict, Any, Optional, Union
from PIL import Image

logger = logging.getLogger(__name__)


class AIInferenceError(Exception):
    """Custom exception raised when AI image inference fails."""
    pass


class OpenCLIPService:
    """
    OpenCLIP image embedding and visual similarity matching service.
    
    Uses pretrained vision transformers (e.g. ViT-B-32) to generate normalized 
    feature vectors and compute cosine similarity for campus lost-and-found items.
    """

    def __init__(self, model_name: str = "ViT-B-32", pretrained: str = "openai"):
        self.model_name = model_name
        self.pretrained = pretrained
        self._model = None
        self._preprocess = None
        self._device = None
        self._initialized = False
        self._is_available = False
        self._status_message = "OpenCLIP service has not been initialized."

    def initialize(self, force_reload: bool = False) -> bool:
        """
        Lazily initialize and cache the OpenCLIP model in memory.
        Detects GPU (CUDA) or CPU automatically and handles missing dependencies or
        download failures gracefully.
        """
        if self._initialized and not force_reload:
            return self._is_available

        self._initialized = True

        try:
            import torch
            import open_clip
        except ImportError as exc:
            self._is_available = False
            self._status_message = (
                f"OpenCLIP dependencies not available ({str(exc)}). "
                "Ensure torch and open-clip-torch are installed."
            )
            logger.warning(self._status_message)
            return False

        try:
            device = "cuda" if torch.cuda.is_available() else "cpu"
            logger.info("Initializing OpenCLIP model %s (%s) on %s...", self.model_name, self.pretrained, device)
            
            model, _, preprocess = open_clip.create_model_and_transforms(
                self.model_name,
                pretrained=self.pretrained,
                device=device,
            )
            model.eval()

            self._model = model
            self._preprocess = preprocess
            self._device = device
            self._is_available = True
            self._status_message = (
                f"OpenCLIP {self.model_name} ({self.pretrained}) active on {device.upper()}."
            )
            logger.info("OpenCLIP model successfully loaded and cached in memory.")
            return True

        except Exception as exc:
            self._is_available = False
            self._status_message = (
                f"Failed to load OpenCLIP pretrained weights: {str(exc)}. "
                "Verify network connectivity to HuggingFace/GitHub or provide cached weights."
            )
            logger.error(self._status_message)
            return False

    def is_available(self) -> bool:
        """Check if OpenCLIP inference engine is ready for real embeddings."""
        if not self._initialized:
            self.initialize()
        return self._is_available

    def get_status(self) -> Dict[str, Any]:
        """Return diagnostic status of the OpenCLIP service."""
        if not self._initialized:
            self.initialize()
        return {
            "available": self._is_available,
            "model_name": self.model_name,
            "pretrained": self.pretrained,
            "device": self._device or "uninitialized",
            "message": self._status_message,
        }

    def generate_embedding(self, image_source: Union[str, Path, Image.Image]) -> Optional[List[float]]:
        """
        Generate a normalized 512-dimensional embedding vector for an image.
        
        Returns:
            List of floats representing the normalized feature vector, or None if
            AI inference is unavailable or image is unreadable.
        """
        if not self.is_available():
            logger.warning("generate_embedding called but OpenCLIP is unavailable: %s", self._status_message)
            return None

        import torch

        # Load or convert to PIL Image
        try:
            if isinstance(image_source, (str, Path)):
                img_path = Path(image_source)
                if not img_path.is_file():
                    logger.warning("Image file does not exist: %s", img_path)
                    return None
                image = Image.open(img_path).convert("RGB")
            elif isinstance(image_source, Image.Image):
                image = image_source.convert("RGB")
            else:
                logger.warning("Unsupported image source type: %s", type(image_source))
                return None
        except Exception as exc:
            logger.error("Failed to load image for OpenCLIP embedding: %s", exc)
            return None

        # Preprocess and feedforward
        try:
            tensor = self._preprocess(image).unsqueeze(0).to(self._device)
            with torch.no_grad():
                image_features = self._model.encode_image(tensor)
                # L2 normalize
                image_features /= image_features.norm(dim=-1, keepdim=True)
                vector = image_features.squeeze(0).cpu().tolist()

            # Ensure valid non-empty float list
            if isinstance(vector, list) and len(vector) > 0:
                return [float(x) for x in vector]
            return None

        except Exception as exc:
            logger.error("OpenCLIP forward pass error: %s", exc)
            return None

    @staticmethod
    def calculate_cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
        """
        Compute standard cosine similarity between two numerical vectors.
        Returns a float between -1.0 and 1.0.
        """
        if not vec1 or not vec2:
            return 0.0

        if len(vec1) != len(vec2):
            logger.warning("Vector dimension mismatch: %d vs %d", len(vec1), len(vec2))
            return 0.0

        dot_product = sum(a * b for a, b in zip(vec1, vec2))
        norm_a = math.sqrt(sum(a * a for a in vec1))
        norm_b = math.sqrt(sum(b * b for b in vec2))

        if norm_a == 0.0 or norm_b == 0.0:
            return 0.0

        sim = dot_product / (norm_a * norm_b)
        return max(-1.0, min(1.0, sim))

    def find_potential_matches(
        self,
        target_report,
        candidates: List[Any],
        threshold: float = 0.60,
        limit: int = 6
    ) -> List[Dict[str, Any]]:
        """
        Rank candidate reports against a target report using cosine similarity.
        
        Rules:
        - Target must have a valid embedding.
        - Candidate must not be the target report itself.
        - Candidate must be the opposite report type (Lost compares with Found, Found with Lost).
        - Candidate must be open status and have a valid embedding.
        - Cosine similarity must be >= threshold.
        - Results sorted descending by similarity.
        """
        target_embedding = target_report.get_embedding()
        if not target_embedding:
            return []

        target_type = target_report.report_type.lower()
        opposite_type = "found" if target_type == "lost" else "lost"

        matches = []
        for candidate in candidates:
            # Exclude self
            if candidate.id == target_report.id:
                continue

            # Must be opposite report type
            if candidate.report_type.lower() != opposite_type:
                continue

            # Must be active / open
            if getattr(candidate, "status", "open") != "open":
                continue

            cand_embedding = candidate.get_embedding()
            if not cand_embedding:
                continue

            similarity = self.calculate_cosine_similarity(target_embedding, cand_embedding)

            if similarity >= threshold:
                percentage = round(similarity * 100.0, 1)
                matches.append({
                    "report": candidate,
                    "similarity": round(similarity, 4),
                    "percentage": percentage,
                })

        # Rank by similarity descending
        matches.sort(key=lambda m: m["similarity"], reverse=True)
        return matches[:limit]


# Global singleton instance of the AI service
ai_service = OpenCLIPService()
