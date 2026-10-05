import json
import tempfile
import unittest
from pathlib import Path
import sys
import zipfile
sys.path.insert(0, str(Path(__file__).parents[1]))
from excel_to_wordbook import normalize_lemma, parse_workbook, write_wordbook_directory

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
        path = Path(r'C:/Users/20212/Downloads/四级词汇全书.xlsx')
        book, errors = parse_workbook(path, 'cet4', '四级词汇', 'CET-4')
        self.assertGreater(len(book['cards']), 4000)
        self.assertEqual(book['cards'][0]['cardId'].split(':', 1)[0], 'cet4')
        self.assertEqual(book['sourcePolicy'], '应用内学习分组，不是官方考试大纲词表。')
        self.assertIsInstance(errors, list)

if __name__ == '__main__':
    unittest.main()
