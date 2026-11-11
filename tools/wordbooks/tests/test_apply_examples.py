import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from apply_examples import merge_book


def card(lemma):
    return {
        "cardId": f"cet4:{lemma}",
        "lemma": lemma,
        "rank": 1,
        "ipa": "/x/",
        "senses": [{"pos": "n.", "meaningZh": "释义"}],
        "example": "暂无例句",
        "exampleZh": None,
        "derived": [],
        "phrases": [],
        "synonyms": [],
    }


class MergeBookTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.assets = root / "assets"
        self.examples = root / "examples"
        (self.assets / "cet4").mkdir(parents=True)
        self.examples.mkdir(parents=True)
        book = {"formatVersion": 1, "id": "cet4", "displayName": "四级", "level": "CET-4", "cards": [card("ability"), card("able")]}
        self.raw = json.dumps(book, ensure_ascii=False, indent=2)
        (self.assets / "cet4" / "book.json").write_text(self.raw, encoding="utf-8")
        sidecar = {"bookId": "cet4", "promptVersion": 1, "model": "m", "entries": {"ability": {"example": "She had the ability to help.", "exampleZh": "她有帮忙的能力。"}}, "failures": {}}
        (self.examples / "cet4.json").write_text(json.dumps(sidecar, ensure_ascii=False, indent=2), encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def read(self):
        return json.loads((self.assets / "cet4" / "book.json").read_text(encoding="utf-8"))

    def test_only_example_fields_change(self):
        before = self.read()
        result = merge_book(self.assets, self.examples, "cet4")
        after = self.read()
        self.assertEqual(result["applied"], 1)
        self.assertEqual(result["missingExamples"], 1)
        self.assertEqual(after["cards"][0]["example"], "She had the ability to help.")
        self.assertEqual(after["cards"][0]["exampleZh"], "她有帮忙的能力。")
        for index in range(2):
            for key in before["cards"][index]:
                if key in {"example", "exampleZh"}:
                    continue
                self.assertEqual(before["cards"][index][key], after["cards"][index][key], f"字段 {key} 不该变")
        self.assertEqual(before["cards"][1], after["cards"][1], "没有例句的卡必须原样保留占位")
        self.assertEqual({k: v for k, v in before.items() if k != "cards"}, {k: v for k, v in after.items() if k != "cards"})

    def test_merge_is_idempotent(self):
        merge_book(self.assets, self.examples, "cet4")
        first = (self.assets / "cet4" / "book.json").read_bytes()
        merge_book(self.assets, self.examples, "cet4")
        second = (self.assets / "cet4" / "book.json").read_bytes()
        self.assertEqual(first, second)

    def test_missing_sidecar_leaves_book_untouched(self):
        (self.examples / "cet4.json").unlink()
        result = merge_book(self.assets, self.examples, "cet4")
        self.assertTrue(result.get("skipped"))
        self.assertEqual((self.assets / "cet4" / "book.json").read_text(encoding="utf-8"), self.raw)

    def test_rewritten_book_is_refused(self):
        (self.assets / "cet4" / "book.json").write_text('{"id": "cet4", "cards": []}', encoding="utf-8")
        with self.assertRaises(SystemExit):
            merge_book(self.assets, self.examples, "cet4")


if __name__ == "__main__":
    unittest.main()
