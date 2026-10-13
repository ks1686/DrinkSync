"""Pure drink math shared by the Pi service; mirrors the phone's ounce conversion."""

from __future__ import annotations

import math

GRAMS_PER_OZ = 29.5735
MIN_DRINK_GRAMS = 5.0


def next_drink(
    previous_weight: float | None,
    current_weight: float | None,
    min_grams: float = MIN_DRINK_GRAMS,
) -> tuple[float | None, float | None]:
    """Return (grams_to_send_or_None, new_previous_weight).

    The first reading only sets a baseline. A drop of at least min_grams is a
    sip. A rise of at least min_grams is a refill. Anything smaller is noise.
    """
    if current_weight is None or not math.isfinite(current_weight):
        return None, previous_weight
    current = float(current_weight)
    if previous_weight is None:
        return None, current
    previous = float(previous_weight)
    delta = previous - current
    if delta >= min_grams:
        return delta, current
    if -delta >= min_grams:
        return None, current
    return None, previous


def round_half_up(value: float) -> int:
    """Match Kotlin's Double.roundToInt(); Python's round() uses banker's rounding."""
    return math.floor(value + 0.5)


def leftover_after_adding(existing_leftover_grams: float, added_grams: float) -> tuple[int, float]:
    """Pair leftover grams into whole ounces the same way the phone does."""
    total = float(existing_leftover_grams) + float(added_grams)
    ounces = round_half_up(total / GRAMS_PER_OZ)
    if ounces <= 0:
        return 0, total
    return ounces, total - ounces * GRAMS_PER_OZ
