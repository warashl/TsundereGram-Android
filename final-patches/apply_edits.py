#!/usr/bin/env python3
"""TsundereGram: точечные фиксы поверх дерева 13.1.1 (после merge-upstream.sh и rebrand.py).

Каждая правка — точная замена old -> new; если точного совпадения нет, ищем то же место
без учёта отступов. Правки одного файла применяются вместе: если хоть одна не нашлась,
файл остаётся как был, чтобы сборка не сломалась из-за половины фикса.
"""
import json
import os
import sys


def find_loose(src, old):
    sl = src.split("\n")
    ol = [l.strip() for l in old.split("\n")]
    st = [l.strip() for l in sl]
    n = len(ol)
    hits = [i for i in range(len(sl) - n + 1) if st[i:i + n] == ol]
    return (hits[0], n) if len(hits) == 1 else None


def apply_one(src, old, new):
    c = src.count(old)
    if c == 1:
        return src.replace(old, new, 1)
    if c == 0 and old != new and src.count(new) == 1:
        return src  # уже применено
    if c == 0:
        hit = find_loose(src, old)
        if hit:
            i, n = hit
            sl = src.split("\n")
            return "\n".join(sl[:i] + new.split("\n") + sl[i + n:])
    return None


def main():
    root, edits_path = sys.argv[1], sys.argv[2]
    edits = json.load(open(edits_path, encoding="utf-8"))["edits"]
    by_file = {}
    for e in edits:
        by_file.setdefault(e["path"], []).append(e)
    ok, skipped = 0, []
    for path, items in by_file.items():
        full = os.path.join(root, path)
        name = os.path.basename(path)
        if not os.path.exists(full):
            skipped.append("%s (нет файла)" % name)
            continue
        raw = open(full, encoding="utf-8", newline="").read()
        crlf = "\r\n" in raw
        src = raw.replace("\r\n", "\n")
        bad = None
        for e in items:
            res = apply_one(src, e["old"], e["new"])
            if res is None:
                bad = e.get("label", "?")
                break
            src = res
        if bad:
            skipped.append("%s (не нашёл место: %s)" % (name, bad))
            continue
        if crlf:
            src = src.replace("\n", "\r\n")
        open(full, "w", encoding="utf-8", newline="").write(src)
        ok += len(items)
        print("OK   %s: %d" % (name, len(items)))
    summary = "Фиксы: применено %d из %d" % (ok, len(edits))
    if skipped:
        summary += "; пропущено: " + ", ".join(skipped)
    for s in skipped:
        print("::warning::Фикс не применён: " + s)
    print(summary)
    for var, line in (("GITHUB_ENV", "FINAL_PATCH_SUMMARY=" + summary), ("GITHUB_STEP_SUMMARY", summary)):
        dest = os.environ.get(var)
        if dest:
            with open(dest, "a", encoding="utf-8") as f:
                f.write(line.replace("\n", " ") + "\n")


if __name__ == "__main__":
    main()
