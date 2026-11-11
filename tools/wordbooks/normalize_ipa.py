#!/usr/bin/env python3
"""Normalize the `ipa` / `senses[].pos` two columns of a bundled word book.

Why this exists (2026-11-11 field report): 304 of the 45,290 shipped cards carried the
placeholder string `/- /` in `ipa`, and 300 of those had the real IPA sitting in
`senses[0].pos` instead — the source workbook put the pronunciation in the 词性 column.
On screen that surfaced as a literal `/- /` under the headword in the search list and the
card detail page.

The fix is a deterministic, idempotent rewrite of the two columns:

* an IPA-looking `pos` is moved into `ipa`, and `pos` falls back to `词汇`;
* a dash-only placeholder `ipa` (with nothing to recover) becomes the empty string, which the
  UI already treats as "no pronunciation" (`isNotBlank()` guards in CardDetailScreen /
  GlobalVocabularySearchScreen / VocabularyRelearnScreen);
* everything else is left byte-identical.

Only `ipa` and `senses[*].pos` are touched; no other field is read or rewritten.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

PLACEHOLDER = re.compile(r"^[\s/\\\-–—~·]*$")
IPA_SHAPED = re.compile(r"^/[^/]{1,62}/$")
# 词性缩写被斜杠包起来（`/n./`、`/vt./`）不是音标，别把它们搬进 ipa 列。
POS_ABBREVIATIONS = frozenset({
    "n", "v", "vt", "vi", "adj", "adv", "prep", "conj", "pron", "art", "num", "int", "aux", "abbr",
});
FALLBACK_POS = "词汇"


def is_placeholder(value: str | None) -> bool:
    return PLACEHOLDER.match((value or "").strip()) is not None


def is_ipa(value: str | None) -> bool:
    """True only for a real pronunciation, not for a part of speech wrapped in slashes (`/n./`)."""
    text = (value or "").strip()
    if IPA_SHAPED.match(text) is None:
        return False
    inner = text.strip("/").strip().rstrip(".").strip()
    return inner.casefold() not in POS_ABBREVIATIONS

def normalize_card(card: dict) -> bool:
    """Rewrite one card in place. Returns True when anything changed."""
    before = (card.get("ipa"), [s.get("pos") for s in card.get("senses") or []])
    ipa = (card.get("ipa") or "").strip()
    senses = card.get("senses") or []
    if not senses:
        senses = [{"pos": FALLBACK_POS, "meaningZh": ""}]
        card["senses"] = senses
    if is_placeholder(ipa):
        recovered = next((s for s in senses if is_ipa(s.get("pos"))), None)
        if recovered is not None:
            ipa = recovered["pos"].strip()
            recovered["pos"] = FALLBACK_POS
        else:
            ipa = ""
        card["ipa"] = ipa
    for sense in senses:
        pos = (sense.get("pos") or "").strip()
        if not pos:
            sense["pos"] = FALLBACK_POS
            continue
        # `/n./`、`/vt./` 是词性不是音标：只脱掉外面那层斜杠，别把词性搬进 ipa 列。
        if IPA_SHAPED.match(pos) is not None and pos.strip("/").rstrip(".").strip().casefold() in POS_ABBREVIATIONS:
            sense["pos"] = pos.strip("/").strip()
    after = (card.get("ipa"), [s.get("pos") for s in senses])
    return before != after


def normalize_book(book: dict) -> int:
    return sum(1 for card in book.get("cards") or [] if normalize_card(card))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", type=Path, required=True, help="path to book.json (or a directory containing one)")
    parser.add_argument("--write", action="store_true", help="write the file back (default: report only)")
    args = parser.parse_args()
    path = args.book / "book.json" if args.book.is_dir() else args.book
    book = json.loads(path.read_text(encoding="utf-8"))
    changed = normalize_book(book)
    if args.write and changed:
        path.write_text(json.dumps(book, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"book": path.name, "changed": changed, "written": bool(args.write and changed)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
