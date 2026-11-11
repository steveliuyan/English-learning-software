#!/usr/bin/env python3
"""Convert the supplied Chinese vocabulary workbooks to the app's book.json format.

The converter is deliberately dependency-free: XLSX files are ZIP/XML documents,
so conversion works in the isolated Python runtime without installing packages.
"""
from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {
    "m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
POLICY = "应用内学习分组，不是官方考试大纲词表。"
HEADERS = ("序号", "单词", "词性", "音标", "中文释义")


def normalize_lemma(value: str) -> str:
    return " ".join(value.strip().casefold().split())


PLACEHOLDER_IPA = re.compile(r"^[\s/\\\-–—~·]*$")
IPA_SHAPED = re.compile(r"^/[^/]{1,62}/$")
# 词性缩写被斜杠包起来（`/n./`、`/vt./`）不是音标。
POS_ABBREVIATIONS = frozenset({
    "n", "v", "vt", "vi", "adj", "adv", "prep", "conj", "pron", "art", "num", "int", "aux", "abbr",
});
FALLBACK_POS = "词汇"


def is_ipa_text(value: str) -> bool:
    """True only for a real pronunciation, not for a part of speech wrapped in slashes (`/n./`)."""
    text = (value or "").strip()
    if IPA_SHAPED.match(text) is None:
        return False
    inner = text.strip("/").strip().rstrip(".").strip()
    return inner.casefold() not in POS_ABBREVIATIONS


def split_pronunciation(pos_raw: str, ipa_raw: str) -> tuple[str, str]:
    """Return (pos, ipa) with the part-of-speech and pronunciation columns un-mixed.

    2026-11-11 field report: 304 shipped cards carried the placeholder `/- /` in `ipa`, and 300 of
    them had the real IPA typed into the 词性 column instead. A placeholder is not a value: an absent
    pronunciation must stay absent (empty string) so the UI's `isNotBlank()` guard hides the row,
    and an IPA-shaped `pos` is moved to where it belongs.
    """
    pos = (pos_raw or "").strip()
    ipa = (ipa_raw or "").strip()
    if PLACEHOLDER_IPA.match(ipa):
        if is_ipa_text(pos):
            ipa, pos = pos, FALLBACK_POS
        else:
            ipa = ""
    return (pos or FALLBACK_POS), ipa


def _shared_strings(zf: zipfile.ZipFile) -> list[str]:
    name = "xl/sharedStrings.xml"
    if name not in zf.namelist():
        return []
    root = ET.fromstring(zf.read(name))
    return ["".join(t.text or "" for t in si.iter(f"{{{NS['m']}}}t")) for si in root]


def _cell_value(cell: ET.Element, shared: list[str]) -> str:
    value = cell.find("m:v", NS)
    text = "" if value is None else (value.text or "")
    if cell.attrib.get("t") == "s" and text:
        return shared[int(text)]
    return text


def _sheet_targets(zf: zipfile.ZipFile) -> list[tuple[str, str]]:
    workbook = ET.fromstring(zf.read("xl/workbook.xml"))
    rels_root = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    rels = {r.attrib["Id"]: r.attrib["Target"] for r in rels_root}
    result = []
    for sheet in workbook.find("m:sheets", NS):
        rid = sheet.attrib[f"{{{NS['r']}}}id"]
        target = rels[rid]
        result.append((sheet.attrib["name"], target if target.startswith("xl/") else "xl/" + target))
    return result


def _rows(zf: zipfile.ZipFile, target: str, shared: list[str]) -> list[list[str]]:
    root = ET.fromstring(zf.read(target))
    result = []
    for row in root.findall(".//m:sheetData/m:row", NS):
        cells = row.findall("m:c", NS)
        values = []
        for cell in cells:
            ref = cell.attrib.get("r", "")
            col = re.sub(r"\d+$", "", ref)
            values.append((col, _cell_value(cell, shared)))
        if not values:
            continue
        by_col = {col: value for col, value in values}
        # Workbook sheets are consistently A:I, but use column positions so a
        # sparse row does not shift the semantic fields.
        result.append([by_col.get(chr(ord("A") + i), "") for i in range(9)])
    return result


def _split_items(value: str) -> list[str]:
    return [x.strip() for x in re.split(r"[；;。\n]+", value or "") if x.strip()]


def _derived(value: str) -> list[dict]:
    result = []
    for item in _split_items(value)[:3]:
        match = re.match(r"([^\s]+)\s+([^\s]+)\s+(.+)", item)
        if match:
            result.append({"lemma": match.group(1), "pos": match.group(2), "meaningZh": match.group(3)[:30]})
    return result


def _related(value: str) -> list[dict]:
    result = []
    for item in _split_items(value)[:3]:
        parts = item.split(None, 1)
        if parts:
            result.append({"lemma": parts[0], "pos": "词汇", "meaningZh": (parts[1] if len(parts) > 1 else parts[0])[:30]})
    return result


def parse_workbook(path: Path, book_id: str, display_name: str, level: str) -> tuple[dict, list[str]]:
    cards = []
    errors = []
    seen: set[str] = set()
    with zipfile.ZipFile(path) as zf:
        shared = _shared_strings(zf)
        for sheet_name, target in _sheet_targets(zf):
            rows = _rows(zf, target, shared)
            header_index = next((i for i, row in enumerate(rows) if all(h in row for h in HEADERS)), None)
            if header_index is None:
                continue
            for row_number, row in enumerate(rows[header_index + 1 :], header_index + 2):
                lemma_raw = row[1].strip()
                if not lemma_raw or lemma_raw == "单词":
                    continue
                lemma = normalize_lemma(lemma_raw)
                if lemma in seen:
                    errors.append(f"{sheet_name}!{row_number}: duplicate lemma {lemma}")
                    continue
                seen.add(lemma)
                pos, ipa = split_pronunciation(row[2], row[3])
                meaning = row[4].strip()
                if not meaning:
                    errors.append(f"{sheet_name}!{row_number}: missing meaning")
                    continue
                cards.append({
                    "cardId": f"{book_id}:{lemma}",
                    "lemma": lemma,
                    "rank": len(cards) + 1,
                    "ipa": ipa[:64],
                    "senses": [{"pos": pos[:64], "meaningZh": meaning[:30]}],
                    "example": "暂无例句",
                    "exampleZh": None,
                    "derived": _derived(row[5]),
                    "phrases": [{"text": x[:64], "meaningZh": x[:30]} for x in _split_items(row[6])[:3]],
                    "synonyms": _related(row[7]),
                })
    if not cards:
        raise ValueError(f"{path.name}: no vocabulary rows found")
    book = {
        "formatVersion": 1,
        "id": book_id,
        "displayName": display_name,
        "level": level,
        "sourceId": "ngsl-nawl-1.2",
        "sourcePolicy": POLICY,
        "attribution": f"用户提供的《{path.stem}》；仅作本地学习资料。",
        "cards": cards,
    }
    return book, errors


def write_wordbook_directory(book: dict, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "book.json").write_text(json.dumps(book, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "manifest.json").write_text(json.dumps({"formatVersion": 1, "images": []}, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--book-id", required=True)
    parser.add_argument("--display-name", required=True)
    parser.add_argument("--level", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    book, errors = parse_workbook(args.input, args.book_id, args.display_name, args.level)
    write_wordbook_directory(book, args.output)
    print(json.dumps({"bookId": args.book_id, "cards": len(book["cards"]), "skipped": errors}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
