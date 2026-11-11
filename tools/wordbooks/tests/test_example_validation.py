import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from example_validation import contains_lemma, count_sentences, parse_response, strip_code_fence, validate


class ParseResponseTest(unittest.TestCase):
    def test_plain_json_is_accepted(self):
        parsed, error = parse_response('{"example": "He has ability.", "exampleZh": "他有能力。"}')
        self.assertIsNone(error)
        self.assertEqual(parsed, {"example": "He has ability.", "exampleZh": "他有能力。"})

    FENCE = "`" * 3

    def test_code_fence_is_stripped(self):
        raw = self.FENCE + "json" + "\n" + '{"example": "He has ability.", "exampleZh": "他有能力。"}' + "\n" + self.FENCE
        parsed, error = parse_response(raw)
        self.assertIsNone(error)
        self.assertEqual(parsed["exampleZh"], "他有能力。")

    def test_extra_fields_are_dropped(self):
        parsed, error = parse_response('{"example": "He has ability.", "exampleZh": "他有能力。", "note": "x"}')
        self.assertIsNone(error)
        self.assertEqual(set(parsed), {"example", "exampleZh"})

    def test_prose_around_json_is_rejected(self):
        parsed, error = parse_response('Here you go: {"example": "He has ability.", "exampleZh": "他有能力。"}')
        self.assertIsNone(parsed)
        self.assertIsNotNone(error)

    def test_missing_field_is_rejected(self):
        parsed, error = parse_response('{"example": "He has ability."}')
        self.assertIsNone(parsed)
        self.assertIn("exampleZh", error)

    def test_blank_field_is_rejected(self):
        parsed, error = parse_response('{"example": "   ", "exampleZh": "他有能力。"}')
        self.assertIsNone(parsed)

    def test_non_json_is_rejected(self):
        parsed, error = parse_response("Sure! Here is a sentence.")
        self.assertIsNone(parsed)
        self.assertIn("not json", error)

    def test_strip_code_fence_without_newline(self):
        self.assertEqual(strip_code_fence(self.FENCE + "json{}" + self.FENCE), "{}")

class ContainsLemmaTest(unittest.TestCase):
    def test_plain_form(self):
        self.assertTrue(contains_lemma("This ability matters.", "ability"))

    def test_plural_and_gerund(self):
        self.assertTrue(contains_lemma("She studies hard every day.", "study"))
        self.assertTrue(contains_lemma("They are studying now.", "study"))
        self.assertTrue(contains_lemma("He stopped the car.", "stop"))
        self.assertTrue(contains_lemma("She is making tea.", "make"))

    def test_multiword_lemma(self):
        self.assertTrue(contains_lemma("I want ice cream tonight.", "ice cream"))

    def test_punctuated_lemmas_match_their_bare_form(self):
        self.assertTrue(contains_lemma("Mr. Smith arrived early.", "mr."))
        self.assertTrue(contains_lemma("They walked toward the door.", "toward(s)"))
        self.assertTrue(contains_lemma("We walked towards the door.", "toward(s)"))
        self.assertTrue(contains_lemma("It is six o'clock now.", "o'clock"))

    def test_optional_letter_and_slash_lemmas_match_either_spelling(self):
        self.assertTrue(contains_lemma("Water vapour rose from the pan.", "vapo(u)r"))
        self.assertTrue(contains_lemma("Water vapor rose from the pan.", "vapo(u)r"))
        self.assertTrue(contains_lemma("He swung the axe twice.", "axe/ax"))
        self.assertTrue(contains_lemma("He swung the ax twice.", "axe/ax"))

    def test_accented_lemma_matches_accented_word(self):
        self.assertTrue(contains_lemma("The ad was a tired cliché.", "cliché"))

    def test_misspelled_lemma_tolerates_one_edit(self):
        self.assertTrue(contains_lemma("He looked through the microscope.", "microsope"))
        self.assertFalse(contains_lemma("He looked through the window.", "microsope"))

    def test_unrelated_word_is_rejected(self):
        self.assertFalse(contains_lemma("This sentence has nothing.", "ability"))


class CountSentencesTest(unittest.TestCase):
    def test_single_and_double(self):
        self.assertEqual(count_sentences("One sentence here."), 1)
        self.assertEqual(count_sentences("First one. Second one!"), 2)


class ValidateTest(unittest.TestCase):
    GOOD_EN = "She had the ability to solve hard problems quickly."
    GOOD_ZH = "她有快速解决难题的能力。"

    def test_good_pair_passes(self):
        ok, reason = validate("ability", self.GOOD_EN, self.GOOD_ZH)
        self.assertTrue(ok, reason)

    def test_too_short_english_is_rejected(self):
        ok, reason = validate("ability", "Ability.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("length", reason)

    def test_english_with_chinese_is_rejected(self):
        ok, reason = validate("ability", "She had the ability 能力 to solve it.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("cjk", reason)

    def test_markdown_is_rejected(self):
        ok, reason = validate("ability", "She had the **ability** to solve it well.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("markdown", reason)

    def test_missing_lemma_is_rejected(self):
        ok, reason = validate("ability", "She had the power to solve hard problems fast.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("lemma", reason)

    def test_three_sentences_are_rejected(self):
        ok, reason = validate("ability", "She had ability. It was clear. We knew that.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("sentences", reason)

    def test_lowercase_start_is_rejected(self):
        ok, reason = validate("ability", "she had the ability to solve hard problems fast.", self.GOOD_ZH)
        self.assertFalse(ok)
        self.assertIn("capital", reason)

    def test_chinese_translation_without_chinese_is_rejected(self):
        ok, reason = validate("ability", self.GOOD_EN, "She has ability.")
        self.assertFalse(ok)
        self.assertIn("chinese", reason)

    def test_short_chinese_is_rejected(self):
        ok, reason = validate("ability", self.GOOD_EN, "能力。")
        self.assertFalse(ok)
        self.assertIn("length", reason)


if __name__ == "__main__":
    unittest.main()
