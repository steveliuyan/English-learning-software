import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from normalize_ipa import FALLBACK_POS, normalize_book, normalize_card


def card(ipa, pos="v.", meaning="听见"):
    return {"cardId": "cet6:hear", "lemma": "hear", "ipa": ipa, "senses": [{"pos": pos, "meaningZh": meaning}]}


class NormalizeCardTest(unittest.TestCase):
    def test_placeholder_ipa_with_real_ipa_in_pos_is_recovered(self):
        c = card("/- /", "/hɪə(r)/")
        self.assertTrue(normalize_card(c))
        self.assertEqual(c["ipa"], "/hɪə(r)/")
        self.assertEqual(c["senses"][0]["pos"], FALLBACK_POS)

    def test_placeholder_ipa_without_any_ipa_becomes_blank(self):
        c = card("/- /", "v.")
        self.assertTrue(normalize_card(c))
        self.assertEqual(c["ipa"], "")
        self.assertEqual(c["senses"][0]["pos"], "v.")

    def test_bare_dash_and_slash_variants_are_placeholders(self):
        for value in ("-", " /- / ", "/ /", "", "——"):
            with self.subTest(value=value):
                c = card(value, "n.")
                normalize_card(c)
                self.assertEqual(c["ipa"], "")

    def test_real_ipa_is_left_untouched(self):
        c = card("/hɪə(r)/", "v.")
        self.assertFalse(normalize_card(c))
        self.assertEqual(c["ipa"], "/hɪə(r)/")
        self.assertEqual(c["senses"][0]["pos"], "v.")

    def test_blank_pos_falls_back_to_a_real_part_of_speech(self):
        c = card("/hɪə(r)/", "  ")
        self.assertTrue(normalize_card(c))
        self.assertEqual(c["senses"][0]["pos"], FALLBACK_POS)

    def test_normalization_is_idempotent(self):
        book = {"cards": [card("/- /", "/hɪə(r)/"), card("/- /", "n."), card("/biː/", "v.")]}
        self.assertEqual(normalize_book(book), 2)
        self.assertEqual(normalize_book(book), 0)

    def test_other_fields_are_never_touched(self):
        c = card("/- /", "/hɪə(r)/", meaning="听见，听到")
        c["example"] = "I can hear the birds."
        c["exampleZh"] = "我能听见鸟叫。"
        c["derived"] = [{"lemma": "hearing", "pos": "n.", "meaningZh": "听力"}]
        normalize_card(c)
        self.assertEqual(c["example"], "I can hear the birds.")
        self.assertEqual(c["exampleZh"], "我能听见鸟叫。")
        self.assertEqual(c["senses"][0]["meaningZh"], "听见，听到")
        self.assertEqual(c["derived"], [{"lemma": "hearing", "pos": "n.", "meaningZh": "听力"}])


    def test_slash_wrapped_part_of_speech_is_not_an_ipa(self):
        c = card("/- /", "/n./")
        normalize_card(c)
        self.assertEqual(c["ipa"], "")
        self.assertEqual(c["senses"][0]["pos"], "n.")

    def test_real_ipa_is_not_overwritten_by_a_slash_wrapped_part_of_speech(self):
        c = card("/ˈempəθi/", "/n./")
        normalize_card(c)
        self.assertEqual(c["ipa"], "/ˈempəθi/")
        self.assertEqual(c["senses"][0]["pos"], "n.")


if __name__ == "__main__":
    unittest.main()
