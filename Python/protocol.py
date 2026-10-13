"""Wire format v1 shared with the Android app: one UTF-8 line per drink event."""

from __future__ import annotations

import math
import re

_CANONICAL = "Weight difference: {grams:.2f} grams"
_MESSAGE = re.compile(
    r"^(?:Weight difference|Average weight|Weight Differnce):\s*"
    r"(-?\d+(?:\.\d+)?)\s*grams$",
    re.IGNORECASE,
)


def format_weight_difference(grams: float) -> str:
    if not math.isfinite(grams):
        raise ValueError(f"grams must be finite, got {grams!r}")
    return _CANONICAL.format(grams=grams)


def encode_message(message: str) -> bytes:
    text = message if message.endswith("\n") else message + "\n"
    return text.encode("utf-8")


def parse_weight_difference(message: str) -> float | None:
    match = _MESSAGE.match(message.strip())
    if match is None:
        return None
    grams = float(match.group(1))
    if not math.isfinite(grams) or grams <= 0:
        return None
    return grams
