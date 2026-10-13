"""Load and save the persisted HX711 calibration (scale_config.json)."""

from __future__ import annotations

import json
import math
import os
import tempfile
from typing import Any


def _optional_number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return value if math.isfinite(value) else None


def load_scale_config(path: str) -> dict[str, Any]:
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    if "offset" not in data or "referenceUnit" not in data:
        raise ValueError("scale config missing offset or referenceUnit")
    offset = _optional_number(data["offset"])
    reference_unit = _optional_number(data["referenceUnit"])
    if offset is None or reference_unit is None or reference_unit == 0:
        raise ValueError("scale config offset/referenceUnit must be finite numbers (referenceUnit non-zero)")
    return {
        "offset": offset,
        "referenceUnit": reference_unit,
        "initialMaxWeight": _optional_number(data.get("initialMaxWeight")),
        "lastWeight": _optional_number(data.get("lastWeight")),
    }


def save_scale_config(path: str, config: dict[str, Any]) -> None:
    """Write atomically so a power cut mid-write cannot corrupt the calibration."""
    payload = {
        "offset": config["offset"],
        "referenceUnit": config["referenceUnit"],
    }
    max_weight = _optional_number(config.get("initialMaxWeight"))
    last_weight = _optional_number(config.get("lastWeight"))
    if max_weight is not None:
        payload["initialMaxWeight"] = max_weight
    if last_weight is not None:
        payload["lastWeight"] = last_weight
    directory = os.path.dirname(os.path.abspath(path))
    fd, tmp_path = tempfile.mkstemp(dir=directory, prefix=".scale_config.", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=4)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, path)
    except BaseException:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise
