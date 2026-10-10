import os
from pathlib import Path
from dotenv import load_dotenv
from app import create_app

# Load environment configuration from .env
env_path = Path(__file__).resolve().parent / ".env"
load_dotenv(dotenv_path=env_path)

env_name = os.getenv("FLASK_ENV", "development")
app = create_app(config_name=env_name)

if __name__ == "__main__":
    host = os.getenv("FLASK_RUN_HOST", "127.0.0.1")
    port = int(os.getenv("FLASK_RUN_PORT", "5000"))
    debug = os.getenv("FLASK_DEBUG", "1") in ("1", "true", "True")
    
    print("=" * 60)
    print("  CampusFind AI - Lost & Found Management System")
    print(f"  Running on: http://{host}:{port}")
    print(f"  Environment: {env_name.capitalize()} (Debug={debug})")
    print("=" * 60)
    
    app.run(host=host, port=port, debug=debug)
