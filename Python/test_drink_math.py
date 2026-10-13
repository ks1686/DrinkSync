import unittest

from drink_math import GRAMS_PER_OZ, MIN_DRINK_GRAMS, leftover_after_adding, next_drink, round_half_up


class NextDrinkTest(unittest.TestCase):
    def test_first_reading_adopts_weight_and_sends_nothing(self):
        grams, previous = next_drink(None, 300.0)
        self.assertIsNone(grams)
        self.assertEqual(previous, 300.0)

    def test_sip_sends_only_the_drop(self):
        grams, previous = next_drink(300.0, 250.0)
        self.assertAlmostEqual(grams, 50.0)
        self.assertEqual(previous, 250.0)

    def test_second_sip_is_not_cumulative(self):
        _, after_first = next_drink(300.0, 250.0)
        grams, after_second = next_drink(after_first, 200.0)
        self.assertAlmostEqual(grams, 50.0)
        self.assertEqual(after_second, 200.0)

    def test_refill_sends_nothing_and_adopts_new_weight(self):
        grams, previous = next_drink(200.0, 310.0)
        self.assertIsNone(grams)
        self.assertEqual(previous, 310.0)

    def test_noise_below_threshold_keeps_previous(self):
        grams, previous = next_drink(200.0, 197.0)
        self.assertIsNone(grams)
        self.assertEqual(previous, 200.0)

    def test_exact_minimum_counts_as_a_drink(self):
        grams, previous = next_drink(200.0, 200.0 - MIN_DRINK_GRAMS)
        self.assertAlmostEqual(grams, MIN_DRINK_GRAMS)
        self.assertEqual(previous, 195.0)


class LeftoverGramsTest(unittest.TestCase):
    def test_small_sip_stays_in_leftover(self):
        ounces, leftover = leftover_after_adding(0.0, 10.0)
        self.assertEqual(ounces, 0)
        self.assertAlmostEqual(leftover, 10.0)

    def test_leftover_plus_sip_can_make_one_ounce(self):
        ounces, leftover = leftover_after_adding(10.0, 20.0)
        self.assertEqual(ounces, 1)
        self.assertAlmostEqual(leftover, 30.0 - 29.5735, places=4)

    def test_half_ounce_rounds_up_like_kotlin_round_to_int(self):
        # Kotlin's 2.5.roundToInt() == 3; Python's round(2.5) == 2.
        ounces, _ = leftover_after_adding(0.0, 2.5 * GRAMS_PER_OZ)
        self.assertEqual(ounces, 3)
        self.assertEqual(round_half_up(0.5), 1)
        self.assertEqual(round_half_up(0.49), 0)


class NonFiniteReadingTest(unittest.TestCase):
    def test_nan_reading_does_not_poison_baseline(self):
        grams, previous = next_drink(None, float("nan"))
        self.assertIsNone(grams)
        self.assertIsNone(previous)
        grams, previous = next_drink(300.0, float("inf"))
        self.assertIsNone(grams)
        self.assertEqual(previous, 300.0)


if __name__ == "__main__":
    unittest.main()
