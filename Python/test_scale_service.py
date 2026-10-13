import unittest
from unittest.mock import patch

import scale_persistent_tare as scale


class FakeHx:
    def __init__(self, weight):
        self.weight = weight

    def get_weight(self, times):
        return self.weight

    def power_down(self):
        pass

    def power_up(self):
        pass

    def get_offset(self):
        return 1

    def get_reference_unit(self):
        return 2


class TakeReadingTest(unittest.TestCase):
    def setUp(self):
        patches = [
            patch.object(scale, "TAKE_READING_DURATION_S", 0.01),
            patch.object(scale, "TAKE_READING_SAMPLE_DELAY", 0),
            patch.object(scale, "persist_last_weight", lambda: None),
            patch.object(scale.time, "sleep", lambda s: None),
            patch.object(scale, "initial_max_weight", None),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def run_reading(self, previous, current, send_ok=True):
        sent = []

        def fake_send(message):
            sent.append(message)
            return send_ok

        with patch.object(scale, "hx", FakeHx(current)), patch.object(scale, "send_message", fake_send):
            scale.last_weight = previous
            grams = scale.take_reading()
        return grams, sent, scale.last_weight

    def test_sip_sends_drop_and_moves_baseline(self):
        grams, sent, baseline = self.run_reading(300.0, 250.0)
        self.assertAlmostEqual(grams, 50.0)
        self.assertEqual(sent, ["Weight difference: 50.00 grams"])
        self.assertEqual(baseline, 250.0)

    def test_failed_send_keeps_baseline_so_drink_is_not_lost(self):
        grams, sent, baseline = self.run_reading(300.0, 250.0, send_ok=False)
        self.assertIsNone(grams)
        self.assertEqual(len(sent), 1)
        self.assertEqual(baseline, 300.0)

    def test_refill_sends_nothing(self):
        grams, sent, baseline = self.run_reading(100.0, 300.0)
        self.assertIsNone(grams)
        self.assertEqual(sent, [])
        self.assertEqual(baseline, 300.0)

    def test_implausible_drop_is_discarded(self):
        with patch.object(scale, "initial_max_weight", 20.0):
            grams, sent, _ = self.run_reading(300.0, 250.0)
        self.assertIsNone(grams)
        self.assertEqual(sent, [])


if __name__ == "__main__":
    unittest.main()
