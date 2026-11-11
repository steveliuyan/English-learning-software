import json
import tempfile
import unittest
from pathlib import Path
import sys
import zipfile
sys.path.insert(0, str(Path(__file__).parents[1]))
from excel_to_wordbook import (
    FALLBACK_POS,
    normalize_lemma,
    parse_workbook,
    split_pronunciation,
    write_wordbook_directory,
)

class ConverterTest(unittest.TestCase):
    def test_normalize_lemma_is_case_and_space_insensitive(self):
        self.assertEqual(normalize_lemma('  New   YORK '), 'new york')

    def test_write_wordbook_directory_is_reproducible_and_has_manifest(self):
        book = {
            'formatVersion': 1,
            'id': 'demo',
            'displayName': '演示',
            'level': '基础',
            'sourceId': 'ngsl-nawl-1.2',
            'sourcePolicy': '应用内学习分组，不是官方考试大纲词表。',
            'attribution': 'test',
            'cards': [{'cardId': 'demo:ability', 'lemma': 'ability'}],
        }
        with tempfile.TemporaryDirectory() as root:
            first = Path(root) / 'first'
            second = Path(root) / 'second'
            write_wordbook_directory(book, first)
            write_wordbook_directory(book, second)
            self.assertEqual((first / 'book.json').read_bytes(), (second / 'book.json').read_bytes())
            self.assertEqual(json.loads((first / 'manifest.json').read_text()), {'formatVersion': 1, 'images': []})

    def test_real_workbook_extracts_all_vocabulary_sheets(self):
        candidates = [
            Path(r'H:/xwechat_files/wxid_er4oix4ay4wi22_36f0/msg/file/2026-11/四级词汇全书.xlsx'),
            Path(r'C:/Users/20212/Downloads/四级词汇全书.xlsx'),
        ]
        path = next((candidate for candidate in candidates if candidate.exists()), None)
        if path is None:
            self.skipTest('本机没有四级词汇全书.xlsx 原件，跳过真实工作簿用例')
        book, errors = parse_workbook(path, 'cet4', '四级词汇', 'CET-4')
        self.assertGreater(len(book['cards']), 4000)
        self.assertEqual(book['cards'][0]['cardId'].split(':', 1)[0], 'cet4')
        self.assertEqual(book['sourcePolicy'], '应用内学习分组，不是官方考试大纲词表。')
        self.assertIsInstance(errors, list)

if __name__ == '__main__':
    unittest.main()


class SplitPronunciationTest(unittest.TestCase):
    """2026-11-11: 304 shipped cards had `/- /` in ipa, 300 of them with the IPA in the pos column."""

    def test_real_ipa_and_pos_are_kept(self):
        self.assertEqual(split_pronunciation("v.", "/hɪə(r)/"), ("v.", "/hɪə(r)/"))

    def test_ipa_typed_into_the_pos_column_is_moved_back(self):
        self.assertEqual(split_pronunciation("/hɪə(r)/", "/- /"), (FALLBACK_POS, "/hɪə(r)/"))
        self.assertEqual(split_pronunciation("/hɪə(r)/", ""), (FALLBACK_POS, "/hɪə(r)/"))

    def test_missing_pronunciation_stays_empty_never_a_placeholder(self):
        for pos in ("v.", "", "   "):
            with self.subTest(pos=pos):
                part, ipa = split_pronunciation(pos, "/- /")
                self.assertEqual(ipa, "")
                self.assertEqual(part, pos.strip() or FALLBACK_POS)

    def test_bare_dash_and_slash_variants_count_as_missing(self):
        for value in ("-", "/ /", "——", "/-/"):
            with self.subTest(value=value):
                self.assertEqual(split_pronunciation("n.", value), ("n.", ""))
