import json
import os
import tempfile
import unittest
from unittest.mock import patch


class ScaleConfigLoadTest(unittest.TestCase):
    def test_config_without_initial_max_weight_is_valid(self):
        from scale_config import load_scale_config

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "scale_config.json")
            with open(path, "w", encoding="utf-8") as handle:
                json.dump({"offset": 140173.33, "referenceUnit": 425.37}, handle)
            loaded = load_scale_config(path)
        self.assertEqual(loaded["offset"], 140173.33)
        self.assertEqual(loaded["referenceUnit"], 425.37)
        self.assertIsNone(loaded["initialMaxWeight"])
        self.assertIsNone(loaded["lastWeight"])

    def test_round_trip_preserves_last_weight(self):
        from scale_config import load_scale_config, save_scale_config

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "scale_config.json")
            save_scale_config(
                path,
                {
                    "offset": 1,
                    "referenceUnit": 425.37,
                    "lastWeight": 248.5,
                },
            )
            loaded = load_scale_config(path)
        self.assertEqual(loaded["lastWeight"], 248.5)
        self.assertIsNone(loaded["initialMaxWeight"])

    def test_rejects_non_numeric_calibration(self):
        from scale_config import load_scale_config

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "scale_config.json")
            with open(path, "w", encoding="utf-8") as handle:
                json.dump({"offset": "abc", "referenceUnit": 0}, handle)
            with self.assertRaises(ValueError):
                load_scale_config(path)

    def test_ignores_non_finite_optional_values(self):
        from scale_config import load_scale_config

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "scale_config.json")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write('{"offset": 1, "referenceUnit": 2, "lastWeight": NaN, "initialMaxWeight": true}')
            loaded = load_scale_config(path)
        self.assertIsNone(loaded["lastWeight"])
        self.assertIsNone(loaded["initialMaxWeight"])

    def test_save_is_atomic_and_leaves_no_temp_files(self):
        from scale_config import save_scale_config

        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "scale_config.json")
            save_scale_config(path, {"offset": 1, "referenceUnit": 2})
            with patch("scale_config.json.dump", side_effect=OSError("disk full")), self.assertRaises(OSError):
                save_scale_config(path, {"offset": 9, "referenceUnit": 9})
            self.assertEqual(os.listdir(tmp), ["scale_config.json"])
            with open(path, encoding="utf-8") as handle:
                self.assertEqual(json.load(handle), {"offset": 1, "referenceUnit": 2})


if __name__ == "__main__":
    unittest.main()
