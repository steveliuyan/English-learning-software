#!/usr/bin/env python3
"""把 sidecar 里的例句幂等合并进随包词书（F1-08 产线的第二段）。

只写 example / exampleZh 两个字段；其余字段逐字节不动。
重复执行结果不变；sidecar 里没有的词条保持「暂无例句」。

    python apply_examples.py --dry-run
    python apply_examples.py --data-version-tag examples-20261111
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PLACEHOLDER = "暂无例句"


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_assets_dir() -> Path:
    return repo_root() / "app" / "src" / "main" / "assets" / "wordbooks"


def default_examples_dir() -> Path:
    return Path(__file__).resolve().parent / "examples"


def merge_book(assets_dir: Path, examples_dir: Path, book_id: str) -> dict:
    book_path = assets_dir / book_id / "book.json"
    sidecar_path = examples_dir / f"{book_id}.json"
    if not sidecar_path.exists():
        return {"bookId": book_id, "skipped": True, "reason": "no sidecar"}

    raw = book_path.read_text(encoding="utf-8")
    book = json.loads(raw)
    if json.dumps(book, ensure_ascii=False, indent=2) != raw:
        raise SystemExit(f"{book_id}: book.json 不是本仓产线的标准格式，合并会重排字节，先核对再动")

    entries = json.loads(sidecar_path.read_text(encoding="utf-8")).get("entries", {})
    applied = 0
    missing = []
    for card in book["cards"]:
        entry = entries.get(card["lemma"])
        if entry is None:
            missing.append(card["lemma"])
            continue
        if card.get("example") != entry["example"] or card.get("exampleZh") != entry["exampleZh"]:
            card["example"] = entry["example"]
            card["exampleZh"] = entry["exampleZh"]
            applied += 1

    merged = json.dumps(book, ensure_ascii=False, indent=2)
    changed = merged != raw
    if changed:
        book_path.write_text(merged, encoding="utf-8")
    return {
        "bookId": book_id,
        "applied": applied,
        "cards": len(book["cards"]),
        "missingExamples": len(missing),
        "changed": changed,
    }


def bump_data_version(assets_dir: Path, tag: str) -> list[dict]:
    path = assets_dir / "metadata.json"
    raw = path.read_text(encoding="utf-8")
    metadata = json.loads(raw)
    for item in metadata:
        base = item.get("dataVersion", "").split("+")[0]
        item["dataVersion"] = f"{base}+{tag}"
    path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return metadata


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="把例句 sidecar 合并进随包词书")
    parser.add_argument("--assets-dir", type=Path, default=default_assets_dir())
    parser.add_argument("--examples-dir", type=Path, default=default_examples_dir())
    parser.add_argument("--book", action="append", default=None)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--data-version-tag", default=None, help="合并成功后把 metadata 的 dataVersion 改成 <base>+<tag>")
    args = parser.parse_args(argv)

    metadata = json.loads((args.assets_dir / "metadata.json").read_text(encoding="utf-8"))
    book_ids = [item["id"] for item in metadata]
    if args.book:
        book_ids = [book_id for book_id in book_ids if book_id in args.book]

    if args.dry_run:
        report = []
        for book_id in book_ids:
            sidecar_path = args.examples_dir / f"{book_id}.json"
            entries = json.loads(sidecar_path.read_text(encoding="utf-8")).get("entries", {}) if sidecar_path.exists() else {}
            cards = json.loads((args.assets_dir / book_id / "book.json").read_text(encoding="utf-8"))["cards"]
            covered = sum(1 for card in cards if card["lemma"] in entries)
            report.append(
                {
                    "bookId": book_id,
                    "cards": len(cards),
                    "sidecarEntries": len(entries),
                    "covered": covered,
                    "coverage": round(covered / len(cards), 4) if cards else 0.0,
                }
            )
        print(json.dumps({"dryRun": True, "books": report}, ensure_ascii=False, indent=2))
        return 0

    results = [merge_book(args.assets_dir, args.examples_dir, book_id) for book_id in book_ids]
    if args.data_version_tag:
        bump_data_version(args.assets_dir, args.data_version_tag)
    print(json.dumps({"books": results}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
