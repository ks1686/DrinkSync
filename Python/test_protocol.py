import unittest

from protocol import format_weight_difference, parse_weight_difference


class FormatWeightDifferenceTest(unittest.TestCase):
    def test_canonical_line_uses_two_decimal_places(self):
        self.assertEqual(
            format_weight_difference(123.4),
            "Weight difference: 123.40 grams",
        )

    def test_zero_formats_but_is_not_a_drink_event(self):
        self.assertEqual(
            format_weight_difference(0),
            "Weight difference: 0.00 grams",
        )


class ParseWeightDifferenceTest(unittest.TestCase):
    def test_canonical_line(self):
        self.assertEqual(
            parse_weight_difference("Weight difference: 12.50 grams"),
            12.5,
        )

    def test_average_weight_alias(self):
        self.assertEqual(
            parse_weight_difference("Average weight: 29.57 grams"),
            29.57,
        )

    def test_legacy_typo_alias(self):
        self.assertEqual(
            parse_weight_difference("Weight Differnce: 8.00 grams"),
            8.0,
        )

    def test_allows_extra_whitespace(self):
        self.assertEqual(
            parse_weight_difference("  Weight difference:   4 grams\n"),
            4.0,
        )

    def test_rejects_junk(self):
        self.assertIsNone(parse_weight_difference("hello"))
        self.assertIsNone(parse_weight_difference(""))
        self.assertIsNone(parse_weight_difference("Weight difference: abc grams"))

    def test_rejects_non_positive_grams(self):
        self.assertIsNone(parse_weight_difference("Weight difference: 0 grams"))
        self.assertIsNone(parse_weight_difference("Weight difference: -3.2 grams"))


if __name__ == "__main__":
    unittest.main()
