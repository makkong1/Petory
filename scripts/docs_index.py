#!/usr/bin/env python3
"""docs/ 전체를 스캔해 YAML frontmatter가 붙은 문서만 모아 docs/INDEX.md를 생성한다.

frontmatter 스키마 (모든 필드 선택):
    ---
    date: 2026-07-12
    domains: [board, care]   # 여러 도메인 걸치면 배열
    type: performance-evidence
    problem: n-plus-one
    status: verified
    metric: "301->3 queries"
    before_commit: 19b7c120   # 실제 before 코드가 존재하던 커밋. 재구성이면 'reconstructed'
    after_commit: 19b7c120    # 해결이 반영된 커밋
    related: [docs/troubleshooting/board/performance-optimization.md]
    ---

frontmatter가 없는 문서는 인덱스에 포함되지 않는다 (파일 이동/강제 태깅 없이
점진적으로 확장). 재실행하면 항상 현재 docs/ 상태를 반영해 덮어쓴다 - 수동 편집 금지.
"""
import os
import re
import sys
from collections import defaultdict

try:
    import yaml
except ImportError:
    sys.exit("PyYAML이 필요합니다: pip install pyyaml")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = os.path.join(REPO_ROOT, "docs")
OUTPUT_FILE = os.path.join(DOCS_DIR, "INDEX.md")
GITHUB_REPO = "makkong1/Petory"

FRONTMATTER_RE = re.compile(r"^---\n(.*?\n)---\n", re.DOTALL)


def scan_docs():
    entries = []
    for root, _, files in os.walk(DOCS_DIR):
        for name in files:
            if not name.endswith(".md"):
                continue
            path = os.path.join(root, name)
            if os.path.abspath(path) == OUTPUT_FILE:
                continue
            with open(path, encoding="utf-8") as f:
                head = f.read(4096)
            m = FRONTMATTER_RE.match(head)
            if not m:
                continue
            try:
                meta = yaml.safe_load(m.group(1)) or {}
            except yaml.YAMLError as e:
                print(f"⚠️  frontmatter 파싱 실패, 스킵: {path} ({e})", file=sys.stderr)
                continue
            rel_path = os.path.relpath(path, REPO_ROOT)
            title = extract_title(head[m.end():])
            entries.append({
                "path": rel_path,
                "title": title,
                "date": str(meta.get("date", "")),
                "domains": meta.get("domains", []) or [],
                "type": meta.get("type", ""),
                "problem": meta.get("problem", ""),
                "status": meta.get("status", ""),
                "metric": meta.get("metric", ""),
                "before_commit": str(meta.get("before_commit", "")),
                "after_commit": str(meta.get("after_commit", "")),
            })
    return entries


def extract_title(body):
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def render_table(entries, columns):
    header = "| " + " | ".join(columns) + " |"
    sep = "| " + " | ".join("---" for _ in columns) + " |"
    lines = [header, sep]
    for e in entries:
        row = []
        for col in columns:
            if col == "문서":
                row.append(f"[{e['title'] or e['path']}]({os.path.relpath(e['path'], 'docs')})")
            elif col == "도메인":
                row.append(", ".join(e["domains"]) or "-")
            elif col == "날짜":
                row.append(e["date"] or "-")
            elif col == "유형":
                row.append(e["type"] or "-")
            elif col == "문제":
                row.append(e["problem"] or "-")
            elif col == "수치":
                row.append(e["metric"] or "-")
            elif col == "상태":
                row.append(e["status"] or "-")
            elif col == "커밋":
                row.append(format_commits(e["before_commit"], e["after_commit"]))
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def format_commits(before, after):
    if not before and not after:
        return "-"
    if before == "reconstructed":
        return "재구성(커밋 없음)"
    if before and after and before == after:
        return commit_link(before)
    parts = []
    if before:
        parts.append(f"전:{commit_link(before)}")
    if after:
        parts.append(f"후:{commit_link(after)}")
    return " ".join(parts)


def commit_link(sha):
    short = sha[:8]
    return f"[{short}](https://github.com/{GITHUB_REPO}/commit/{sha})"


def main():
    entries = scan_docs()
    entries.sort(key=lambda e: e["date"], reverse=True)

    if not entries:
        print("frontmatter가 붙은 문서가 없습니다.", file=sys.stderr)

    by_domain = defaultdict(list)
    for e in entries:
        for d in (e["domains"] or ["(미지정)"]):
            by_domain[d].append(e)

    by_problem = defaultdict(list)
    for e in entries:
        by_problem[e["problem"] or "(미지정)"].append(e)

    out = []
    out.append("<!-- 자동 생성 파일 — 직접 수정하지 말 것. `python3 scripts/docs_index.py`로 재생성 -->")
    out.append("# 문서 인덱스 (자동 생성)")
    out.append("")
    out.append(f"frontmatter가 붙은 문서 {len(entries)}건. "
                "각 문서 상단에 `date/domains/type/problem/status/metric` YAML을 붙이면 자동으로 여기 잡힌다.")
    out.append("")

    out.append("## 날짜순")
    out.append("")
    out.append(render_table(entries, ["날짜", "문서", "도메인", "문제", "수치", "커밋"]))
    out.append("")

    out.append("## 도메인별")
    out.append("")
    for domain in sorted(by_domain):
        out.append(f"### {domain}")
        out.append("")
        out.append(render_table(by_domain[domain], ["날짜", "문서", "문제", "수치"]))
        out.append("")

    out.append("## 문제 유형별")
    out.append("")
    for problem in sorted(by_problem):
        out.append(f"### {problem}")
        out.append("")
        out.append(render_table(by_problem[problem], ["날짜", "문서", "도메인", "수치"]))
        out.append("")

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")

    print(f"✅ {OUTPUT_FILE} 생성 완료 ({len(entries)}건)")


if __name__ == "__main__":
    main()
