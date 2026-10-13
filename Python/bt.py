"""RFCOMM client that delivers drink events to the DrinkSync Android app."""

from __future__ import annotations

import os
import re
import time
from typing import Any, Callable

from protocol import encode_message

try:
    import bluetooth
except ImportError:  # PyBluez is only present on the Pi.
    bluetooth = None

SERVICE_UUID = "c7506ec6-09d3-4979-9db3-3b85acad20fd"  # Must match the Android side.
ACK_TIMEOUT_S = 2.0
MAC_ENV_VAR = "DRINKSYNC_BT_MAC"
DEFAULT_ATTEMPTS = 3
DEFAULT_RETRY_DELAY_S = 1.0

_MAC_PATTERN = re.compile(r"^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")


def is_valid_mac(address: str) -> bool:
    return bool(_MAC_PATTERN.match(address))


def resolve_target_address(environ: dict[str, str] | None = None) -> str | None:
    """Return the phone's Bluetooth address from DRINKSYNC_BT_MAC, or None if unset or malformed."""
    env = os.environ if environ is None else environ
    address = env.get(MAC_ENV_VAR, "").strip()
    if not address:
        print(f"{MAC_ENV_VAR} is not set; export it to your phone's Bluetooth address (AA:BB:CC:DD:EE:FF).")
        return None
    if not is_valid_mac(address):
        print(f"{MAC_ENV_VAR}={address!r} is not a valid Bluetooth address (expected AA:BB:CC:DD:EE:FF).")
        return None
    return address.upper()


def _send_once(bt: Any, target_address: str, payload: bytes) -> bool:
    service_matches = bt.find_service(uuid=SERVICE_UUID, address=target_address)
    if not service_matches:
        print("Could not find the DrinkSync service. Is the app open on the phone?")
        return False

    first_match = service_matches[0]
    sock = bt.BluetoothSocket(bt.RFCOMM)
    try:
        sock.connect((first_match["host"], first_match["port"]))
        sock.settimeout(ACK_TIMEOUT_S)
        sock.send(payload)
        try:
            data = sock.recv(1024)
            print("Received:", data.decode("utf-8", errors="replace").strip())
        except OSError as e:
            print(f"No ACK within {ACK_TIMEOUT_S:.0f}s (send still succeeded): {e}")
        return True
    finally:
        try:
            sock.close()
        except OSError as e:
            print(f"Warning: error closing Bluetooth socket: {e}")


def send_message(
    message: str,
    *,
    attempts: int = DEFAULT_ATTEMPTS,
    retry_delay_s: float = DEFAULT_RETRY_DELAY_S,
    bt: Any = None,
    sleep: Callable[[float], None] = time.sleep,
) -> bool:
    """Send one drink event line, retrying the service lookup and connect on failure.

    Returns True once a send completes. A missing ACK still counts as success.
    """
    bt = bt if bt is not None else bluetooth
    if bt is None:
        print("PyBluez is not installed; cannot send Bluetooth messages.")
        return False

    target_address = resolve_target_address()
    if target_address is None:
        return False

    payload = encode_message(message)
    for attempt in range(1, max(1, attempts) + 1):
        try:
            if _send_once(bt, target_address, payload):
                return True
        except Exception as e:  # PyBluez raises BluetoothError, OSError, and others.
            print(f"Error sending message (attempt {attempt}/{attempts}): {e}")
        if attempt < attempts:
            sleep(retry_delay_s * attempt)
    return False
