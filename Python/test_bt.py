import unittest
from unittest.mock import patch

import bt


class FakeSocket:
    def __init__(self, owner, fail_connect=False, ack=b"OK\n"):
        self.owner = owner
        self.fail_connect = fail_connect
        self.ack = ack
        self.sent = []
        self.closed = False

    def connect(self, addr):
        if self.fail_connect:
            raise OSError("connection refused")

    def settimeout(self, timeout):
        pass

    def send(self, payload):
        self.sent.append(payload)

    def recv(self, size):
        if self.ack is None:
            raise OSError("timed out")
        return self.ack

    def close(self):
        self.closed = True


class FakeBluetooth:
    RFCOMM = 3

    def __init__(self, services=None, connect_failures=0, ack=b"OK\n"):
        self.services = services if services is not None else [{"host": "AA:BB:CC:DD:EE:FF", "port": 1}]
        self.connect_failures = connect_failures
        self.ack = ack
        self.sockets = []
        self.lookups = []

    def find_service(self, uuid, address):
        self.lookups.append((uuid, address))
        return self.services

    def BluetoothSocket(self, proto):  # noqa: N802 - mirrors the PyBluez API
        sock = FakeSocket(self, fail_connect=len(self.sockets) < self.connect_failures, ack=self.ack)
        self.sockets.append(sock)
        return sock


ENV = {"DRINKSYNC_BT_MAC": "aa:bb:cc:dd:ee:ff"}


class ResolveAddressTest(unittest.TestCase):
    def test_uses_environment(self):
        self.assertEqual(bt.resolve_target_address(ENV), "AA:BB:CC:DD:EE:FF")

    def test_no_hardcoded_fallback(self):
        self.assertIsNone(bt.resolve_target_address({}))
        self.assertFalse(hasattr(bt, "DEFAULT_TARGET_ADDRESS"))

    def test_rejects_malformed_address(self):
        self.assertIsNone(bt.resolve_target_address({"DRINKSYNC_BT_MAC": "not-a-mac"}))
        self.assertIsNone(bt.resolve_target_address({"DRINKSYNC_BT_MAC": "AA:BB:CC:DD:EE"}))


class SendMessageTest(unittest.TestCase):
    def send(self, fake, **kwargs):
        with patch.dict("os.environ", ENV, clear=True):
            return bt.send_message("Weight difference: 12.50 grams", bt=fake, sleep=lambda s: None, **kwargs)

    def test_sends_newline_terminated_line_and_closes(self):
        fake = FakeBluetooth()
        self.assertTrue(self.send(fake))
        self.assertEqual(fake.lookups, [(bt.SERVICE_UUID, "AA:BB:CC:DD:EE:FF")])
        self.assertEqual(fake.sockets[0].sent, [b"Weight difference: 12.50 grams\n"])
        self.assertTrue(fake.sockets[0].closed)

    def test_missing_ack_still_counts_as_sent(self):
        self.assertTrue(self.send(FakeBluetooth(ack=None)))

    def test_retries_after_connect_failure(self):
        fake = FakeBluetooth(connect_failures=2)
        self.assertTrue(self.send(fake, attempts=3))
        self.assertEqual(len(fake.sockets), 3)
        self.assertTrue(all(s.closed for s in fake.sockets))

    def test_gives_up_after_attempts(self):
        fake = FakeBluetooth(connect_failures=5)
        self.assertFalse(self.send(fake, attempts=2))
        self.assertEqual(len(fake.sockets), 2)

    def test_service_not_found_is_retried_then_fails(self):
        fake = FakeBluetooth(services=[])
        self.assertFalse(self.send(fake, attempts=3))
        self.assertEqual(len(fake.lookups), 3)

    def test_without_address_does_not_touch_bluetooth(self):
        fake = FakeBluetooth()
        with patch.dict("os.environ", {}, clear=True):
            self.assertFalse(bt.send_message("x", bt=fake, sleep=lambda s: None))
        self.assertEqual(fake.lookups, [])


if __name__ == "__main__":
    unittest.main()
