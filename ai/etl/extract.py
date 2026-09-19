"""Extract control-input log documents from MongoDB.

The backend writes directly to the same MongoDB collection that we read here, so
there's no HTTP / auth round trip. If you need to re-run the analytics from a
machine that can't reach Mongo, use the parquet export from a previous run.
"""
from __future__ import annotations

import logging
from typing import Iterator

import uuid

from bson.binary import STANDARD, Binary, UuidRepresentation
from pymongo import ASCENDING
from pymongo.collection import Collection

from etl.load import get_collection

logger = logging.getLogger(__name__)


def iter_documents(collection: Collection, batch_size: int = 1000) -> Iterator[dict]:
    cursor = collection.find({}, projection={"_id": 0}).sort("recordedAt", ASCENDING).batch_size(batch_size)
    count = 0
    for doc in cursor:
        count += 1
        if count % batch_size == 0:
            logger.info("Streamed %d documents", count)
        yield doc
    logger.info("Total documents streamed: %d", count)


def fetch_all(mongo_uri: str, db_name: str, collection_name: str) -> list[dict]:
    collection = get_collection(mongo_uri, db_name, collection_name)
    return list(iter_documents(collection))


def _session_id_query(session_id: str) -> dict:
    """Build a sessionId filter that matches docs written under any of the
    common UUID BSON representations. The Spring backend can write subtype 3
    (Java legacy) or subtype 4 (standard) depending on driver config — query
    both so the dashboard works regardless."""
    try:
        u = uuid.UUID(session_id)
    except ValueError:
        return {"sessionId": session_id}

    candidates = [
        u,  # native (matches subtype-4 under uuidRepresentation=standard)
        Binary.from_uuid(u, STANDARD),
        Binary.from_uuid(u, UuidRepresentation.JAVA_LEGACY),
        Binary.from_uuid(u, UuidRepresentation.PYTHON_LEGACY),
        Binary.from_uuid(u, UuidRepresentation.CSHARP_LEGACY),
        str(u),
    ]
    return {"sessionId": {"$in": candidates}}


def fetch_by_session(mongo_uri: str, db_name: str, collection_name: str,
                     session_id: str) -> list[dict]:
    collection = get_collection(mongo_uri, db_name, collection_name)
    cursor = collection.find(
        _session_id_query(session_id), projection={"_id": 0}
    ).sort("recordedAt", ASCENDING)
    return list(cursor)
