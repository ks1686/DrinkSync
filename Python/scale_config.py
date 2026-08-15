import json


def load_scale_config(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    if "offset" not in data or "referenceUnit" not in data:
        raise ValueError("scale config missing offset or referenceUnit")
    max_weight = data.get("initialMaxWeight")
    if not isinstance(max_weight, (int, float)):
        max_weight = None
    return {
        "offset": data["offset"],
        "referenceUnit": data["referenceUnit"],
        "initialMaxWeight": max_weight,
    }
