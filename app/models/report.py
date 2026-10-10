import json
from datetime import datetime, date
from typing import Optional, List
from app.extensions import db


REPORT_CATEGORIES = [
    "Bags",
    "Phones",
    "Laptops",
    "Electronics",
    "Wallets",
    "Keys",
    "ID Cards",
    "Books",
    "Clothing",
    "Accessories",
    "Other",
]

REPORT_TYPES = ["lost", "found"]
REPORT_STATUSES = ["open", "resolved", "closed"]


class Report(db.Model):
    """Model representing a lost or found item report."""
    __tablename__ = "reports"

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False, index=True)
    report_type = db.Column(db.String(10), nullable=False, index=True)  # 'lost' or 'found'
    category = db.Column(db.String(50), nullable=False, index=True)
    description = db.Column(db.Text, nullable=False)
    date_incident = db.Column(db.Date, nullable=False, default=date.today)
    location = db.Column(db.String(150), nullable=False, index=True)
    contact_info = db.Column(db.String(150), nullable=False)
    status = db.Column(db.String(20), default="open", nullable=False, index=True)  # 'open', 'resolved', 'closed'
    
    # Optional image and AI embedding storage
    image_filename = db.Column(db.String(255), nullable=True)
    embedding_json = db.Column(db.Text, nullable=True)

    # Foreign key to user
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False, index=True)
    author = db.relationship("User", back_populates="reports")

    # Timestamps
    created_at = db.Column(db.DateTime, default=datetime.utcnow, nullable=False, index=True)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    def get_embedding(self) -> Optional[List[float]]:
        """Retrieve the serialized OpenCLIP float embedding vector."""
        if not self.embedding_json:
            return None
        try:
            vector = json.loads(self.embedding_json)
            if isinstance(vector, list) and len(vector) > 0:
                return vector
        except (json.JSONDecodeError, TypeError, ValueError):
            pass
        return None

    def set_embedding(self, vector: Optional[List[float]]) -> None:
        """Serialize and store the float embedding vector."""
        if vector is None:
            self.embedding_json = None
        else:
            self.embedding_json = json.dumps([float(x) for x in vector])

    @property
    def has_image(self) -> bool:
        return bool(self.image_filename)

    @property
    def has_embedding(self) -> bool:
        return bool(self.embedding_json)

    @property
    def is_lost(self) -> bool:
        return self.report_type.lower() == "lost"

    @property
    def is_found(self) -> bool:
        return self.report_type.lower() == "found"

    @property
    def is_open(self) -> bool:
        return self.status == "open"

    def __repr__(self) -> str:
        return f"<Report #{self.id} [{self.report_type.upper()}] {self.title} ({self.status})>"
