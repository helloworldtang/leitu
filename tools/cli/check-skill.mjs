#!/usr/bin/env node
// 守门人：skills/leitu/SKILL.md 不得漂移。
// 校验三件事（任何一条不满足即退出码 1）：
//   1. SKILL.md 引用的每个 docs/problems/*.md 与 examples/* 路径真实存在；
//   2. catalog 里每个 answered 条目都被 SKILL.md 路由表覆盖（不许漏）；
//   3. catalog-snapshot.json（npm 发布快照，若存在）与 catalog/problems.json 一致。
// 反向自测方式：故意从 SKILL.md 删一行路由 → 本脚本必须红（见 commit message）。

import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const errors = [];

const catalog = JSON.parse(readFileSync(join(ROOT, "catalog", "problems.json"), "utf8"));
const skill = readFileSync(join(ROOT, "skills", "leitu", "SKILL.md"), "utf8");

// 1. SKILL.md 里引用的仓库路径必须存在
const pathRefs = [...skill.matchAll(/\b((?:docs|examples|catalog)\/[A-Za-z0-9\-\/.]+)/g)].map(
  (m) => m[1].replace(/[).,]+$/, "")
);
for (const p of new Set(pathRefs)) {
  if (!existsSync(join(ROOT, p))) {
    errors.push(`SKILL.md 引用的路径不存在：${p}`);
  }
}

// 2. answered 条目必须全部出现在 SKILL.md（按 id 或 doc 路径匹配）
for (const e of catalog.entries.filter((x) => x.status === "answered")) {
  const hit =
    skill.includes(`/${e.id}`) ||
    skill.includes(e.links.doc) ||
    skill.includes(e.links.example);
  if (!hit) {
    errors.push(`catalog 的 answered 条目「${e.id}」未在 SKILL.md 路由表中覆盖——AI 会被路由漏掉它`);
  }
}

// 3. npm 快照与 catalog 一致（发布链路防漂移）
const snap = join(HERE, "catalog-snapshot.json");
if (existsSync(snap)) {
  const a = readFileSync(snap, "utf8");
  const b = readFileSync(join(ROOT, "catalog", "problems.json"), "utf8");
  if (a !== b) {
    errors.push(
      "tools/cli/catalog-snapshot.json 与 catalog/problems.json 不一致——执行 prepack（cp ../../catalog/problems.json catalog-snapshot.json）后重试"
    );
  }
}

if (errors.length) {
  console.error("SKILL/catalog 守门失败：");
  for (const e of errors) console.error("  - " + e);
  process.exit(1);
}
console.log(
  `SKILL/catalog 守门通过：${new Set(pathRefs).size} 个路径引用有效，` +
    `${catalog.entries.filter((x) => x.status === "answered").length} 条 answered 全覆盖。`
);
