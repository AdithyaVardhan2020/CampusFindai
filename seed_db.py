"""
Database Seeder for CampusFind AI
Populates demo administrative accounts, students, and sample lost & found items
with precomputed OpenCLIP embeddings for instant demonstration of visual AI similarity.
"""

import math
import random
from datetime import date, datetime, timedelta
from app import create_app
from app.extensions import db
from app.models.user import User
from app.models.report import Report


def generate_synthetic_embedding(seed_val: int = 42, base_vector=None, noise: float = 0.05):
    """Generate a reproducible normalized 512-dimension embedding vector."""
    random.seed(seed_val)
    dim = 512
    if base_vector is None:
        raw = [random.gauss(0, 1) for _ in range(dim)]
    else:
        # Perturb base vector slightly to simulate similar photos (e.g. 85-95% similarity)
        raw = [v + random.gauss(0, noise) for v in base_vector]
    norm = math.sqrt(sum(x * x for x in raw))
    return [x / norm for x in raw]


def seed():
    app = create_app()
    with app.app_context():
        print("Initializing database tables...")
        db.create_all()

        if User.query.first():
            print("Database already contains data. Skipping seeding.")
            return

        print("Seeding demo accounts...")
        # 1. Admin Account
        admin = User(
            username="admin",
            email="admin@campus.edu",
            full_name="Campus Security Admin",
            phone="+1 (555) 019-2831",
            role="admin",
        )
        admin.set_password("admin123")
        db.session.add(admin)

        # 2. Student Accounts
        student1 = User(
            username="alex_student",
            email="alex.rivera@campus.edu",
            full_name="Alex Rivera",
            phone="+1 (555) 234-5678",
            role="student",
        )
        student1.set_password("student123")
        db.session.add(student1)

        student2 = User(
            username="priya_patel",
            email="priya.patel@campus.edu",
            full_name="Priya Patel",
            phone="+1 (555) 876-5432",
            role="student",
        )
        student2.set_password("student123")
        db.session.add(student2)

        student3 = User(
            username="marcus_chen",
            email="marcus.chen@campus.edu",
            full_name="Marcus Chen",
            phone="+1 (555) 991-4422",
            role="student",
        )
        student3.set_password("student123")
        db.session.add(student3)

        db.session.commit()

        # Generate base embeddings for paired items
        hydroflask_base = generate_synthetic_embedding(seed_val=101)
        hydroflask_similar = generate_synthetic_embedding(base_vector=hydroflask_base, noise=0.12)

        macbook_base = generate_synthetic_embedding(seed_val=202)
        macbook_similar = generate_synthetic_embedding(base_vector=macbook_base, noise=0.09)

        keys_base = generate_synthetic_embedding(seed_val=303)
        keys_similar = generate_synthetic_embedding(base_vector=keys_base, noise=0.14)

        calculator_base = generate_synthetic_embedding(seed_val=404)
        headphones_base = generate_synthetic_embedding(seed_val=505)

        today = date.today()

        reports = [
            # 1. Lost Hydro Flask (Matches Found Hydro Flask)
            Report(
                title="Midnight Blue Hydro Flask (32oz)",
                report_type="lost",
                category="Other",
                description="Dark blue 32oz wide-mouth Hydroflask with a NASA sticker and slight dent near the bottom rim.",
                date_incident=today - timedelta(days=2),
                location="Science Library 2nd Floor Silent Study",
                contact_info="alex.rivera@campus.edu or text (555) 234-5678",
                status="open",
                user_id=student1.id,
            ),
            # 2. Found Hydro Flask (Candidate Match)
            Report(
                title="Blue Insulated Water Bottle with Stickers",
                report_type="found",
                category="Other",
                description="Found deep blue metal water bottle on study desk #14 with space stickers. Handed to circulation desk.",
                date_incident=today - timedelta(days=1),
                location="Science Library Circulation Desk",
                contact_info="Front Desk attendant - ext 4120",
                status="open",
                user_id=student2.id,
            ),
            # 3. Lost MacBook Pro (Matches Found MacBook)
            Report(
                title="MacBook Pro 14-inch Space Gray (M2)",
                report_type="lost",
                category="Laptops",
                description="Space Gray MacBook in a transparent protective hardshell case with GitHub stickers on the cover lid.",
                date_incident=today - timedelta(days=3),
                location="Student Union Coffee Lounge 1st Floor",
                contact_info="priya.patel@campus.edu",
                status="open",
                user_id=student2.id,
            ),
            # 4. Found MacBook
            Report(
                title="Apple MacBook Space Gray in Clear Case",
                report_type="found",
                category="Laptops",
                description="Turned in to security desk. 14-inch laptop found left unattended on coffee table with tech stickers.",
                date_incident=today - timedelta(days=2),
                location="Campus Police Central Desk (Hall B)",
                contact_info="Central Security Office - ext 2200",
                status="open",
                user_id=admin.id,
            ),
            # 5. Lost Keys
            Report(
                title="Dorm Room Keys on Red Nylon Lanyard",
                report_type="lost",
                category="Keys",
                description="3 keys including brass room key, mailbox key, and gym barcode tag on a crimson university lanyard.",
                date_incident=today - timedelta(days=1),
                location="North Quad Athletic Field",
                contact_info="marcus.chen@campus.edu",
                status="open",
                user_id=student3.id,
            ),
            # 6. Found Keys
            Report(
                title="Set of Brass Keys with Red University Tag",
                report_type="found",
                category="Keys",
                description="Found on athletic bleachers after soccer practice.",
                date_incident=today,
                location="Gymnasium Front Office",
                contact_info="Gym Reception Desk",
                status="open",
                user_id=student1.id,
            ),
            # 7. Lost Headphones
            Report(
                title="Sony WH-1000XM4 Noise-Canceling Headphones",
                report_type="lost",
                category="Electronics",
                description="Matte black over-ear headphones inside black zippered case with audio cables.",
                date_incident=today - timedelta(days=4),
                location="Engineering Hall Room 304",
                contact_info="marcus.chen@campus.edu",
                status="open",
                user_id=student3.id,
            ),
            # 8. Found Calculator
            Report(
                title="TI-84 Plus CE Color Graphing Calculator",
                report_type="found",
                category="Electronics",
                description="White casing graphing calculator with battery charger cable.",
                date_incident=today - timedelta(days=5),
                location="Mathematics Building Room 102",
                contact_info="Math Dept Office Room 100",
                status="resolved",
                user_id=student2.id,
            ),
        ]

        # Attach embeddings to reports
        reports[0].set_embedding(hydroflask_base)
        reports[1].set_embedding(hydroflask_similar)
        reports[2].set_embedding(macbook_base)
        reports[3].set_embedding(macbook_similar)
        reports[4].set_embedding(keys_base)
        reports[5].set_embedding(keys_similar)
        reports[6].set_embedding(headphones_base)
        reports[7].set_embedding(calculator_base)

        for r in reports:
            db.session.add(r)

        db.session.commit()
        print("Database seeded successfully with 4 users and 8 sample reports!")


if __name__ == "__main__":
    seed()
