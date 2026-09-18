"""MongoDB connection helpers.

The backend writes control inputs to Mongo directly, so the data pipeline no
longer performs a load step. This module just exposes the connection helper
used by the extract step and the notebooks.
"""
from __future__ import annotations

from pymongo import MongoClient
from pymongo.collection import Collection


def get_collection(uri: str, db_name: str, collection_name: str) -> Collection:
    # The Spring backend writes UUIDs using the Java driver's legacy BSON
    # binary subtype 3. PyMongo refuses to encode/decode UUIDs unless we tell
    # it which representation the data is in — pick the standard one so both
    # filters with native uuid.UUID and existing legacy docs round-trip.
    client = MongoClient(
        uri,
        serverSelectionTimeoutMS=10_000,
        uuidRepresentation="standard",
    )
    client.admin.command("ping")
    return client[db_name][collection_name]
