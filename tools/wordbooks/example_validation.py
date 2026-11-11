#!/usr/bin/env python3
"""例句的内容契约：解析模型输出 + 校验（F1-08 / AC1-30）。

产线专用，零依赖。所有不合格内容都必须在落盘前被这里挡住：
sidecar 与 book.json 里都不能出现半条不合格例句。
"""
from __future__ import annotations

import json
import re

MIN_EN_CHARS = 20
MAX_EN_CHARS = 160
MIN_ZH_CHARS = 4
MAX_ZH_CHARS = 120
MAX_SENTENCES = 2

REQUIRED_FIELDS = ("example", "exampleZh")

_CJK = re.compile(r"[\u4e00-\u9fff\u3400-\u4dbf\u3000-\u303f\uff01-\uff60]")
_CJK_IDEOGRAPH = re.compile(r"[\u4e00-\u9fff]")
_MARKDOWN = re.compile(r"[*_`#\[\]<>|~]")
_FENCE = re.compile(r"^\s*```[a-zA-Z]*\s*\n?(.*?)\n?\s*```\s*$", re.S)
_TERMINALS = re.compile(r"[.!?]+")


def strip_code_fence(text: str) -> str:
    """只剥「整段被围栏包住」的情形；混杂说明文字的不剥，交由解析失败处理。"""
    match = _FENCE.match(text or "")
    return match.group(1) if match else (text or "")


def parse_response(raw: str) -> tuple[dict | None, str | None]:
    """把模型原文解析成 {"example","exampleZh"}；失败时返回 (None, 原因)。"""
    payload = strip_code_fence(raw).strip()
    if not payload:
        return None, "empty response"
    try:
        data = json.loads(payload)
    except json.JSONDecodeError as error:
        return None, f"not json: {error.msg}"
    if not isinstance(data, dict):
        return None, "not a json object"
    missing = [field for field in REQUIRED_FIELDS if not isinstance(data.get(field), str) or not data.get(field).strip()]
    if missing:
        return None, "missing or empty field: " + ",".join(missing)
    # 多给的字段一律丢弃
    return {field: data[field].strip() for field in REQUIRED_FIELDS}, None


def _inflections(lemma: str) -> set[str]:
    forms = {lemma, lemma + "s", lemma + "es", lemma + "ed", lemma + "d", lemma + "ing", lemma + "er", lemma + "est", lemma + "ly", lemma + "ness"}
    if lemma.endswith("y"):
        stem = lemma[:-1]
        forms.update({stem + "ies", stem + "ied", stem + "ier", stem + "iest"})
    if lemma.endswith("e"):
        stem = lemma[:-1]
        forms.update({stem + "ing", stem + "ed"})
    if len(lemma) > 2:
        # stop -> stopped / stopping
        forms.update({lemma + lemma[-1] + "ed", lemma + lemma[-1] + "ing"})
    return forms


def _lemma_cores(lemma: str) -> list[str]:
    """把词条折成若干可比对的词干。

    覆盖：纯字母词条、"mr." 这类带点词条、"cliché" 这类重音字母、
    "vapo(u)r" 这类括注可选字母、"axe/ax" 这类斜杠并列。
    """
    lemma = lemma.strip()
    if "/" in lemma:
        cores: list[str] = []
        for part in lemma.split("/"):
            cores.extend(_lemma_cores(part))
        return [item for item in cores if item]
    cores = []
    bare = "".join(ch for ch in lemma if ch.isalpha() or ch == "'")
    if bare:
        cores.append(bare)
    # 括注可选字母：with = 保留括号里的字母，without = 丢掉
    match = re.search(r"\(([A-Za-z']+)\)", lemma)
    if match:
        with_group = lemma.replace("(", "").replace(")", "")
        cores.append("".join(ch for ch in with_group if ch.isalpha() or ch == "'"))
        cores.append("".join(ch for ch in lemma[: match.start()] + lemma[match.end():]
                             if ch.isalpha() or ch == "'"))
    expanded: list[str] = []
    for core in cores:
        if core:
            expanded.append(core)
            for suffix in ("s", "es"):
                if core.endswith(suffix) and len(core) - len(suffix) >= 2:
                    expanded.append(core[: -len(suffix)])
    return list(dict.fromkeys(item for item in expanded if item))


def _within_one_edit(left: str, right: str) -> bool:
    if abs(len(left) - len(right)) > 1:
        return False
    if len(left) > len(right):
        left, right = right, left
    if len(left) == len(right):
        return sum(1 for a, b in zip(left, right) if a != b) == 1
    for index in range(len(right)):
        if right[:index] + right[index + 1:] == left:
            return True
    return False


def contains_lemma(sentence: str, lemma: str) -> bool:
    lowered = sentence.casefold()
    if " " in lemma or "-" in lemma:
        return lemma in lowered
    tokens = set(re.findall(r"[^\W\d_]+(?:'[^\W\d_]+)*", lowered))
    for core in _lemma_cores(lemma):
        forms = _inflections(core)
        if tokens & forms:
            return True
        # 词条本身可能带录入笔误（如 "microsope"），放行编辑距离 1 的整词。
        if len(core) >= 5 and any(_within_one_edit(token, form)
                                  for token in tokens for form in forms):
            return True
    return False


def count_sentences(sentence: str) -> int:
    parts = [part for part in _TERMINALS.split(sentence.strip()) if part.strip()]
    return len(parts)


def validate(lemma: str, example: str, example_zh: str) -> tuple[bool, str | None]:
    """通过返回 (True, None)，否则 (False, 原因)。原因会进产线报告。"""
    if not example or not example.strip():
        return False, "example empty"
    if not (MIN_EN_CHARS <= len(example) <= MAX_EN_CHARS):
        return False, f"example length {len(example)} not in {MIN_EN_CHARS}-{MAX_EN_CHARS}"
    if _CJK.search(example):
        return False, "example contains cjk"
    if _MARKDOWN.search(example):
        return False, "example contains markdown"
    if not contains_lemma(example, lemma):
        return False, "example does not contain the lemma"
    if count_sentences(example) > MAX_SENTENCES:
        return False, "example has more than 2 sentences"
    if not example[0].isupper():
        return False, "example does not start with a capital letter"
    if not example_zh or not example_zh.strip():
        return False, "exampleZh empty"
    if not (MIN_ZH_CHARS <= len(example_zh) <= MAX_ZH_CHARS):
        return False, f"exampleZh length {len(example_zh)} not in {MIN_ZH_CHARS}-{MAX_ZH_CHARS}"
    if not _CJK_IDEOGRAPH.search(example_zh):
        return False, "exampleZh contains no chinese"
    return True, None
