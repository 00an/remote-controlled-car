import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


def load_config() -> "Config":
    project_root = Path(__file__).resolve().parent.parent
    load_dotenv(project_root / ".env", override=True)
    return Config(
        mongo_uri=_required("MONGO_URI"),
        mongo_db=os.getenv("MONGO_DB", "nl14"),
        mongo_collection=os.getenv("MONGO_COLLECTION", "control_inputs"),
        mongo_broadcast_collection=os.getenv("MONGO_BROADCAST_COLLECTION", "controller_broadcasts"),
        project_root=project_root,
    )


def _required(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


@dataclass(frozen=True)
class Config:
    mongo_uri: str
    mongo_db: str
    mongo_collection: str
    mongo_broadcast_collection: str
    project_root: Path
