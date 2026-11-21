#!/usr/bin/env python3
"""md -> HTML(mermaid 포함) -> Chrome headless -> PDF"""
import sys, json, pathlib, subprocess, re

src = pathlib.Path(sys.argv[1]).resolve()
out_html = pathlib.Path(sys.argv[2]).resolve()
out_pdf  = pathlib.Path(sys.argv[3]).resolve()

md = src.read_text(encoding="utf-8")
# 메모 주석(<!-- -->)을 전부 제거한다. 앞에 글자가 하나라도 붙으면 ^ 앵커가 빗나가
# 주석 전체가 본문으로 렌더되던 사고가 있어, 위치와 무관하게 지운다. 코드블록 안은 보호.
_cmt = []
md = re.sub(r"```[\s\S]*?```", lambda m: (_cmt.append(m.group(0)), f"@@CMT{len(_cmt)-1}@@")[1], md)
md = re.sub(r"<!--[\s\S]*?-->\s*", "", md)
md = re.sub(r"@@CMT(\d+)@@", lambda m: _cmt[int(m.group(1))], md)

# 한글 조사가 볼드 뒤에 붙으면 CommonMark 규칙상 닫히지 않는다(**92%**였습니다).
# 코드블록을 보호한 뒤 **...** 를 <strong> 으로 직접 바꾼다.
_fences = []
def _keep(m):
    _fences.append(m.group(0)); return f"@@FENCE{len(_fences)-1}@@"
md = re.sub(r"```[\s\S]*?```|`[^`\n]+`", _keep, md)
md = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", md)
md = re.sub(r"@@FENCE(\d+)@@", lambda m: _fences[int(m.group(1))], md)

CSS = """
@page { size: A4; margin: 11mm 12mm; }
:root { --ink:#1a1d21; --dim:#5b6470; --line:#d8dde3; --accent:#1f4e79; --bg:#f4f6f8; }
* { box-sizing: border-box; }
body { font-family:"Apple SD Gothic Neo","Pretendard",-apple-system,sans-serif;
  font-size:9.4pt; line-height:1.5; color:var(--ink); margin:0; word-break:keep-all; }
h1 { font-size:13pt; margin:0 0 7pt; padding-bottom:3pt; color:var(--accent);
     border-bottom:1.6pt solid var(--accent); letter-spacing:-.2pt;
     page-break-after:avoid; page-break-inside:avoid; margin-top:13pt; }
h1:first-of-type { margin-top:0; }
h2 { font-size:10.8pt; margin:12pt 0 5pt; color:var(--accent); }
h3 { font-size:9.8pt; margin:8pt 0 4pt; color:var(--dim); font-weight:600; }
p { margin:0 0 4.6pt; }
strong { font-weight:700; color:#000; }
em { color:var(--dim); font-style:italic; }
hr { display:none; }
ul, ol { margin:0 0 5pt; padding-left:14pt; }
li { margin-bottom:1.6pt; }
table { width:100%; border-collapse:collapse; margin:0 0 6pt; font-size:8.7pt;
        page-break-inside:auto; }
tr { page-break-inside:avoid; }
thead { display:table-header-group; }
th, td { border:.5pt solid var(--line); padding:2.8pt 4pt; text-align:left; vertical-align:top; }
th { background:var(--bg); font-weight:700; color:var(--accent); }
code { font-family:"Menlo",monospace; font-size:8.3pt; background:var(--bg);
       padding:.5pt 2.5pt; border-radius:2pt; color:#8a2f4d; }
pre { background:#f8fafb; border:.5pt solid var(--line); border-left:2pt solid var(--accent);
      padding:4pt 6pt; margin:0 0 6pt; overflow:hidden; page-break-inside:avoid; }
pre code { background:none; padding:0; color:var(--ink); font-size:8.1pt; line-height:1.45;
           white-space:pre-wrap; word-break:break-word; }
blockquote { margin:0 0 6pt; padding:5pt 7pt; background:var(--bg);
             border-left:2pt solid #9aa7b4; page-break-inside:avoid; }
blockquote p:last-child { margin-bottom:0; }
.mermaid { text-align:center; margin:2pt 0 7pt; page-break-inside:avoid; }
.mermaid svg { max-width:100% !important; height:auto !important; }
"""

html = """<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>Petory 포트폴리오 — 박영범</title>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<style>%s</style></head><body>
<div id="out"></div>
<script id="src" type="text/plain">%s</script>
<script>
const raw = document.getElementById('src').textContent;
marked.setOptions({gfm:true, breaks:false});
const blocks = [];
const md = raw.replace(/```mermaid\\n([\\s\\S]*?)```/g, (m, code) => {
  blocks.push(code); return `@@MERMAID${blocks.length-1}@@`;
});
let out = marked.parse(md);
blocks.forEach((code, i) => {
  out = out.replace(`<p>@@MERMAID${i}@@</p>`, `<div class="mermaid">${code.replace(/</g,'&lt;')}</div>`);
});
document.getElementById('out').innerHTML = out;
mermaid.initialize({startOnLoad:false, theme:'neutral',
  themeVariables:{fontFamily:'Apple SD Gothic Neo, sans-serif', fontSize:'15px'}});
document.querySelectorAll('h1').forEach(h => {
  if (/^\s*[5-8]\./.test(h.textContent)) h.style.pageBreakBefore = 'always';
});
mermaid.run().then(()=>{ document.title = 'ready'; });
</script></body></html>""" % (CSS, md.replace("</script>", "<\\/script>"))

out_html.write_text(html, encoding="utf-8")
subprocess.run([
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "--headless", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
    "--virtual-time-budget=20000", f"--print-to-pdf={out_pdf}", out_html.as_uri()
], check=True, capture_output=True)
print(f"OK  {out_pdf}")
