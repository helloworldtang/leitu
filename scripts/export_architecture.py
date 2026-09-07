#!/usr/bin/env python3
"""架构状态导出：catalog → 4R 四图（Rank / Role / Relation / Rule）。

用法：python3 scripts/export_architecture.py（或 make export）
产出：docs/architecture/views.md——「10 分钟看懂现状」的可审计性工件。
本文件是生成器的唯一事实源；views.md 为生成物，勿手改。
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog" / "problems.json"
RULES_FILE = (ROOT / "leitu-core" / "src" / "test" / "java" / "cn" / "youhuale"
              / "leitu" / "core" / "CoreBoundaryRulesTest.java")
OUT = ROOT / "docs" / "architecture" / "views.md"

STATUS_ICON = {"answered": "✅", "drafted": "📝", "open": "⬜",
               "deprecated": "🗑", "removed": "❌"}


def load_entries():
    return json.loads(CATALOG.read_text(encoding="utf-8"))["entries"]


def collect_rules():
    if not RULES_FILE.exists():
        return []
    src = RULES_FILE.read_text(encoding="utf-8")
    return re.findall(r'static final ArchRule\s+(\w+)[\s\S]*?because\("([^"]+)"\)', src)


def section_rank(entries):
    answered = [e for e in entries if e["status"] == "answered"]
    pipeline = [e for e in entries if e["facet"] == "pipeline"]
    capability = [e for e in entries if e["facet"] == "capability"]
    lines = [
        "## Rank——顶层结构（双平面）",
        "",
        "```mermaid",
        "flowchart TB",
        "  subgraph code[代码平面]",
        "    EX[examples 使用面] --> ST[starter 装配面]",
        "    ST --> AD[adapter 绑定面] --> CA[capability 能力面] --> CO[core 端口面]",
        "  end",
        "  subgraph asset[资产平面（答案的多面表示）]",
        "    CAT[catalog 机器地图] --- DOC[docs/problems 答案页]",
        "    DOC --- DEC[decisions 决策链] --- RUL[rules 守护规则] --- EXA[examples 金样本]",
        "  end",
        "  CO -. 答案五件套锚定 .-> CAT",
        "```",
        "",
        f"覆盖度：**{len(answered)} / {len(entries)}** 已答（管道题 {len(pipeline)} 条，"
        f"能力题 {len(capability)} 条）。",
        "",
    ]
    return lines


def section_role(entries):
    lines = ["## Role——各区职责与状态", "", "| id | 面 | 档 | 状态 | 问题 |", "|---|---|---|---|---|"]
    for e in entries:
        icon = STATUS_ICON.get(e["status"], "？")
        lines.append(f"| {e['id']} | {e['facet']} | {e['required']} | {icon} {e['status']} | {e['problem']} |")
    lines.append("")
    return lines


def section_relation(entries):
    lines = [
        "## Relation——答案依赖图",
        "",
        "（AI 学习路径：沿箭头方向先学依赖根；答案级爆炸半径：改动被依赖多的节点前先看下游）",
        "",
        "```mermaid",
        "graph LR",
    ]
    node_ids = set()
    for e in entries:
        for dep in e.get("depends_on") or []:
            lines.append(f"  {e['id']} --> {dep}")
            node_ids.update({e["id"], dep})
    for e in entries:
        if e["id"] in node_ids and e["status"] == "answered":
            lines.append(f"  class {e['id']} answered")
    lines += [
        "  classDef answered fill:#d4edda,stroke:#2e7d32",
        "```",
        "",
    ]
    return lines


def section_rule(rules):
    lines = ["## Rule——守护规则清单", "", "| 规则 | 守什么 |", "|---|---|"]
    for name, because in rules:
        lines.append(f"| `{name}` | {because} |")
    lines += [
        "| `make verify` | 编译 + 测试 + 边界规则一条命令（AGENTS.md 完成判据） |",
        "| `scripts/export_architecture.py` | 本视图的再生成（catalog 变更后跑） |",
        "",
    ]
    return lines


def main():
    entries = load_entries()
    rules = collect_rules()
    content = [
        "# 架构状态（4R 四图）",
        "",
        "> 自动生成（`make export`），勿手改。数据源：catalog/problems.json + 边界规则测试。",
        "",
    ]
    content += section_rank(entries)
    content += section_role(entries)
    content += section_relation(entries)
    content += section_rule(rules)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(content), encoding="utf-8")
    print(f"✓ {OUT.relative_to(ROOT)}（{len(entries)} 条目，{len(rules)} 规则）")


if __name__ == "__main__":
    main()
