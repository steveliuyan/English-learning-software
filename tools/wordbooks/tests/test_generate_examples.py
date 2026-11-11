import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from generate_examples import Sidecar, estimate, generate, resolve_one


def task(lemma, book="cet4", pos="n.", meaning="测试释义"):
    return {"bookId": book, "lemma": lemma, "pos": pos, "meaningZh": meaning, "rank": 1}


def good(lemma):
    return json.dumps(
        {"example": f"The word {lemma} appears in this simple sentence.", "exampleZh": "这是一个简单例句的中文翻译。"},
        ensure_ascii=False,
    )


class FakeRequests:
    def __init__(self, responder):
        self.responder = responder
        self.calls = []

    def __call__(self, task):
        self.calls.append(task["lemma"])
        return self.responder(task)


class GenerateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "cet4.json"

    def tearDown(self):
        self.tmp.cleanup()

    def sidecar(self):
        return Sidecar.load(self.path, "cet4", "test-model")

    def test_resume_skips_already_done(self):
        sidecar = self.sidecar()
        sidecar.accept("alpha", "The word alpha appears in this simple sentence.", "这是一个简单例句的中文翻译。", 1)
        sidecar.save()
        fake = FakeRequests(lambda task: good(task["lemma"]))
        stats = generate([task("alpha"), task("bravo"), task("charlie")], self.sidecar(), fake, retry=0)
        self.assertEqual(fake.calls, ["bravo", "charlie"])
        self.assertEqual(stats["skipped"], 1)
        self.assertEqual(stats["accepted"], 2)
        stored = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(set(stored["entries"]), {"alpha", "bravo", "charlie"})

    def test_invalid_content_is_not_stored(self):
        fake = FakeRequests(lambda task: "Sure! Here you go.")
        sidecar = self.sidecar()
        stats = generate([task("alpha")], sidecar, fake, retry=1, sleep=lambda _: None)
        self.assertEqual(stats["accepted"], 0)
        self.assertEqual(stats["failed"], 1)
        self.assertEqual(sidecar.entries, {})
        self.assertIn("alpha", sidecar.failures)
        stored = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual(stored["entries"], {})
        self.assertEqual(stats["requested"], 2, "重试上限为 retry+1 次请求")

    def test_content_failing_validation_is_not_stored(self):
        fake = FakeRequests(lambda task: json.dumps({"example": "Too short.", "exampleZh": "太短了。"}, ensure_ascii=False))
        sidecar = self.sidecar()
        stats = generate([task("alpha")], sidecar, fake, retry=0, sleep=lambda _: None)
        self.assertEqual(stats["accepted"], 0)
        self.assertEqual(sidecar.entries, {})

    def test_flush_writes_progress_before_interruption(self):
        fake = FakeRequests(lambda task: good(task["lemma"]))
        sidecar = self.sidecar()

        def boom(task):
            raise KeyboardInterrupt

        original = fake.responder

        def responder(task):
            if task["lemma"] == "bravo":
                boom(task)
            return original(task)

        fake.responder = responder
        with self.assertRaises(KeyboardInterrupt):
            generate([task("alpha"), task("bravo"), task("charlie")], sidecar, fake, retry=0, flush_every=1, sleep=lambda _: None)
        stored = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertIn("alpha", stored["entries"], "被中断前已完成的词条必须已经落盘")

    def test_rerun_does_not_rerequest_flushed_entries(self):
        first = FakeRequests(lambda task: good(task["lemma"]))
        generate([task("alpha")], self.sidecar(), first, retry=0, flush_every=1)
        second = FakeRequests(lambda task: good(task["lemma"]))
        stats = generate([task("alpha"), task("bravo")], self.sidecar(), second, retry=0, flush_every=1)
        self.assertEqual(second.calls, ["bravo"])
        self.assertEqual(stats["skipped"], 1)

    def test_estimate_counts_only_pending(self):
        sidecar = self.sidecar()
        sidecar.accept("alpha", "The word alpha appears in this simple sentence.", "这是一个简单例句的中文翻译。", 1)
        summary = estimate([task("alpha"), task("bravo")], sidecar, 0.15, 0.60)
        self.assertEqual(summary["pending"], 1)
        self.assertEqual(summary["alreadyDone"], 1)
        self.assertGreater(summary["inputTokens"], 0)
        self.assertGreaterEqual(summary["costUsd"], 0)

    def test_resolve_one_reports_attempts(self):
        calls = {"n": 0}

        def flaky(task):
            calls["n"] += 1
            return "not json" if calls["n"] == 1 else good(task["lemma"])

        result = resolve_one(task("alpha"), flaky, retry=1)
        self.assertTrue(result["ok"])
        self.assertEqual(result["attempts"], 2)


if __name__ == "__main__":
    unittest.main()
