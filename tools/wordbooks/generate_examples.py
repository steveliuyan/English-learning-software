#!/usr/bin/env python3
"""离线生成词书例句（F1-08 产线的第一段）。

输入是十册 Excel 原件，产出是 sidecar：tools/wordbooks/examples/<bookId>.json。
本脚本**不碰** app/src/main/assets —— 合并进 book.json 走 apply_examples.py。

    python generate_examples.py --excel-dir <目录> --dry-run
    python generate_examples.py --excel-dir <目录> --base-url https://api.x.com/v1 \
        --model <模型名> --api-key <密钥> --concurrency 8

被中断是常态：每完成 --flush-every 条落一次盘，重跑时已完成的词条直接跳过。
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path

from example_validation import parse_response, validate
from excel_to_wordbook import parse_workbook

PROMPT_VERSION = 1

EXCEL_BOOKS = {
    "小学英语词汇全书": "primary-school",
    "初中英语词汇全书": "junior-high-school",
    "高中英语词汇全书": "senior-high-school",
    "四级词汇全书": "cet4",
    "六级英语词汇全书": "cet6",
    "考研英语词汇全书": "kaoyan-english",
    "研究生英语词汇全书": "graduate-english",
    "博士英语词汇全书": "doctoral-english",
    "雅思词汇全书": "ielts",
    "托福词汇全书": "toefl",
}
BOOK_ORDER = list(EXCEL_BOOKS.values())

SYSTEM_PROMPT = (
    "你是英语学习词典的例句作者。只输出一个 JSON 对象，不要解释、不要代码围栏。"
)
USER_TEMPLATE = (
    "单词：{lemma}\n词性：{pos}\n中文释义：{meaningZh}\n"
    "请写一句 8–16 个词的英文例句：必须出现该单词（可为其屈折形式），用常见词与简单句式，"
    "不要引号、不要 Markdown、不超过两句；再给一句对应的中文翻译。\n"
    '只输出：{{"example": "...", "exampleZh": "..."}}'
)


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_assets_dir() -> Path:
    return repo_root() / "app" / "src" / "main" / "assets" / "wordbooks"


def default_examples_dir() -> Path:
    return Path(__file__).resolve().parent / "examples"


def load_metadata(assets_dir: Path) -> dict[str, dict]:
    return {item["id"]: item for item in json.loads((assets_dir / "metadata.json").read_text(encoding="utf-8"))}


def build_tasks(excel_dir: Path, assets_dir: Path, books: list[str] | None = None) -> list[dict]:
    """按「一卡一请求」展开任务；同时核对词条集合与随包资产一致。"""
    metadata = load_metadata(assets_dir)
    tasks: list[dict] = []
    for stem, book_id in EXCEL_BOOKS.items():
        if books and book_id not in books:
            continue
        path = excel_dir / f"{stem}.xlsx"
        if not path.exists():
            raise SystemExit(f"缺少 Excel 原件：{path}")
        meta = metadata[book_id]
        book, errors = parse_workbook(path, book_id, meta["displayName"], meta["level"])
        bundled = json.loads((assets_dir / book_id / "book.json").read_text(encoding="utf-8"))
        excel_lemmas = {card["lemma"] for card in book["cards"]}
        bundled_lemmas = {card["lemma"] for card in bundled["cards"]}
        if excel_lemmas != bundled_lemmas:
            raise SystemExit(
                f"{book_id}: Excel 与随包词条集合不一致（Excel 独有 {len(excel_lemmas - bundled_lemmas)}、"
                f"资产独有 {len(bundled_lemmas - excel_lemmas)}），先核对词书再生成"
            )
        if errors:
            raise SystemExit(f"{book_id}: Excel 有 {len(errors)} 条解析错误，例如 {errors[0]}")
        for card in book["cards"]:
            sense = card["senses"][0] if card["senses"] else {"pos": "词汇", "meaningZh": ""}
            tasks.append(
                {
                    "bookId": book_id,
                    "lemma": card["lemma"],
                    "pos": sense["pos"],
                    "meaningZh": sense["meaningZh"],
                    "rank": card["rank"],
                }
            )
    return tasks


class Sidecar:
    """一个词书一份 sidecar；每次保存都是原子替换。"""

    def __init__(self, path: Path, book_id: str, model: str, prompt_version: int = PROMPT_VERSION):
        self.path = path
        self.data = {
            "bookId": book_id,
            "model": model,
            "promptVersion": prompt_version,
            "entries": {},
            "failures": {},
        }

    @classmethod
    def load(cls, path: Path, book_id: str, model: str, prompt_version: int = PROMPT_VERSION) -> "Sidecar":
        sidecar = cls(path, book_id, model, prompt_version)
        if path.exists():
            stored = json.loads(path.read_text(encoding="utf-8"))
            sidecar.data["entries"] = dict(stored.get("entries", {}))
            sidecar.data["failures"] = dict(stored.get("failures", {}))
            sidecar.data["model"] = stored.get("model", model)
            sidecar.data["promptVersion"] = stored.get("promptVersion", prompt_version)
        return sidecar

    @property
    def entries(self) -> dict:
        return self.data["entries"]

    @property
    def failures(self) -> dict:
        return self.data["failures"]

    def done(self, lemma: str) -> bool:
        return lemma in self.entries

    def accept(self, lemma: str, example: str, example_zh: str, attempts: int) -> None:
        self.entries[lemma] = {
            "example": example,
            "exampleZh": example_zh,
            "model": self.data["model"],
            "promptVersion": self.data["promptVersion"],
            "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "attempts": attempts,
        }
        self.failures.pop(lemma, None)

    def fail(self, lemma: str, reason: str) -> None:
        self.failures[lemma] = reason

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(self.data, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
        os.replace(tmp, self.path)


def build_prompt(task: dict) -> list[dict]:
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": USER_TEMPLATE.format(**task)},
    ]


def http_chat(base_url: str, model: str, api_key: str, messages: list[dict], timeout: float) -> str:
    body = json.dumps({"model": model, "messages": messages, "temperature": 0.3}).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=body,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    return payload["choices"][0]["message"]["content"]


def generate(tasks, sidecar: Sidecar, request_fn, retry: int = 2, flush_every: int = 20, sleep=time.sleep) -> dict:
    """逐条请求、校验、落盘；不合格内容绝不写入。"""
    stats = {"requested": 0, "accepted": 0, "failed": 0, "skipped": 0}
    pending = [task for task in tasks if not sidecar.done(task["lemma"])]
    stats["skipped"] = len(tasks) - len(pending)
    processed = 0
    for task in pending:
        reason = None
        for attempt in range(1, retry + 2):
            stats["requested"] += 1
            try:
                raw = request_fn(task)
            except Exception as error:  # 网络、HTTP、超时都归一类失败
                reason = f"request failed: {error}"
                sleep(min(2 ** attempt * 0.5, 8))
                continue
            parsed, parse_error = parse_response(raw)
            if parsed is None:
                reason = parse_error
                continue
            ok, validation_error = validate(task["lemma"], parsed["example"], parsed["exampleZh"])
            if not ok:
                reason = validation_error
                continue
            sidecar.accept(task["lemma"], parsed["example"], parsed["exampleZh"], attempt)
            stats["accepted"] += 1
            reason = None
            break
        if reason is not None:
            sidecar.fail(task["lemma"], reason)
            stats["failed"] += 1
        processed += 1
        if flush_every and processed % flush_every == 0:
            sidecar.save()
    sidecar.save()
    return stats


def estimate(tasks, sidecar: Sidecar, price_in: float, price_out: float) -> dict:
    pending = [task for task in tasks if not sidecar.done(task["lemma"])]
    prompt_chars = sum(len(SYSTEM_PROMPT) + len(USER_TEMPLATE) + len(task["lemma"]) + len(task["pos"]) + len(task["meaningZh"]) for task in pending)
    input_tokens = prompt_chars // 4
    output_tokens = 70 * len(pending)
    return {
        "pending": len(pending),
        "alreadyDone": len(tasks) - len(pending),
        "inputTokens": input_tokens,
        "outputTokens": output_tokens,
        "costUsd": round(input_tokens / 1_000_000 * price_in + output_tokens / 1_000_000 * price_out, 4),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="离线生成词书例句，产出 sidecar（不写资产）")
    parser.add_argument("--excel-dir", type=Path, required=True)
    parser.add_argument("--assets-dir", type=Path, default=default_assets_dir())
    parser.add_argument("--examples-dir", type=Path, default=default_examples_dir())
    parser.add_argument("--book", action="append", default=None, help="只跑某几册（bookId），可重复")
    parser.add_argument("--limit", type=int, default=0, help="最多处理多少条（0=不限）")
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--retry", type=int, default=2)
    parser.add_argument("--flush-every", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--base-url", default=os.environ.get("WORD_EXAMPLES_BASE_URL", ""))
    parser.add_argument("--model", default=os.environ.get("WORD_EXAMPLES_MODEL", ""))
    parser.add_argument("--api-key", default=os.environ.get("WORD_EXAMPLES_API_KEY", ""))
    parser.add_argument("--price-in", type=float, default=0.15, help="每百万输入 token 的参考价（美元）")
    parser.add_argument("--price-out", type=float, default=0.60, help="每百万输出 token 的参考价（美元）")
    parser.add_argument("--dry-run", action="store_true", help="只报数量与成本，不发任何请求")
    args = parser.parse_args(argv)

    tasks = build_tasks(args.excel_dir, args.assets_dir, args.book)
    if args.limit:
        tasks = tasks[: args.limit]

    grouped: dict[str, list[dict]] = {}
    for task in tasks:
        grouped.setdefault(task["bookId"], []).append(task)

    if args.dry_run:
        report = {"model": args.model or "(未指定)", "books": {}, "total": len(tasks)}
        total_cost = 0.0
        for book_id in BOOK_ORDER:
            if book_id not in grouped:
                continue
            sidecar = Sidecar.load(args.examples_dir / f"{book_id}.json", book_id, args.model or "(未指定)")
            summary = estimate(grouped[book_id], sidecar, args.price_in, args.price_out)
            total_cost += summary["costUsd"]
            report["books"][book_id] = summary
        report["costUsd"] = round(total_cost, 4)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0

    if not args.base_url or not args.model or not args.api_key:
        print("缺少 --base-url / --model / --api-key（或同名环境变量），未发出任何请求。", file=sys.stderr)
        return 2

    def request_fn(task: dict) -> str:
        return http_chat(args.base_url, args.model, args.api_key, build_prompt(task), args.timeout)

    summary = {}
    for book_id in BOOK_ORDER:
        if book_id not in grouped:
            continue
        sidecar = Sidecar.load(args.examples_dir / f"{book_id}.json", book_id, args.model)
        if args.concurrency <= 1:
            stats = generate(grouped[book_id], sidecar, request_fn, args.retry, args.flush_every)
        else:
            stats = generate_parallel(grouped[book_id], sidecar, request_fn, args.retry, args.flush_every, args.concurrency)
        summary[book_id] = stats
        print(json.dumps({"bookId": book_id, **stats}, ensure_ascii=False), flush=True)
    print(json.dumps({"done": summary}, ensure_ascii=False))
    return 0


def generate_parallel(tasks, sidecar: Sidecar, request_fn, retry: int, flush_every: int, concurrency: int) -> dict:
    """并发版：请求在池里跑，落盘只在主线程做，避免并发写 sidecar。"""
    stats = {"requested": 0, "accepted": 0, "failed": 0, "skipped": 0}
    pending = [task for task in tasks if not sidecar.done(task["lemma"])]
    stats["skipped"] = len(tasks) - len(pending)
    processed = 0
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = {pool.submit(resolve_one, task, request_fn, retry): task for task in pending}
        for future in as_completed(futures):
            task = futures[future]
            result = future.result()
            stats["requested"] += result["attempts"]
            if result["ok"]:
                sidecar.accept(task["lemma"], result["example"], result["exampleZh"], result["attempts"])
                stats["accepted"] += 1
            else:
                sidecar.fail(task["lemma"], result["reason"])
                stats["failed"] += 1
            processed += 1
            if flush_every and processed % flush_every == 0:
                sidecar.save()
    sidecar.save()
    return stats


def resolve_one(task: dict, request_fn, retry: int) -> dict:
    reason = None
    attempts = 0
    for attempt in range(1, retry + 2):
        attempts = attempt
        try:
            raw = request_fn(task)
        except Exception as error:
            reason = f"request failed: {error}"
            continue
        parsed, parse_error = parse_response(raw)
        if parsed is None:
            reason = parse_error
            continue
        ok, validation_error = validate(task["lemma"], parsed["example"], parsed["exampleZh"])
        if not ok:
            reason = validation_error
            continue
        return {"ok": True, "attempts": attempts, "example": parsed["example"], "exampleZh": parsed["exampleZh"], "reason": None}
    return {"ok": False, "attempts": attempts, "reason": reason, "example": None, "exampleZh": None}


if __name__ == "__main__":
    raise SystemExit(main())
