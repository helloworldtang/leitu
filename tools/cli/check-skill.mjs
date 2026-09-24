#!/usr/bin/env node
// 守门人：skills/leitu/SKILL.md 不得漂移。校验四件事（任一不满足即退出码 1）：
//   1. SKILL.md 引用的每个 docs/problems/*.md 与 examples/* 路径真实存在；
//   2. catalog 里每个 answered 条目都被 SKILL.md 路由表覆盖（不许漏）；
//   3. catalog-snapshot.json（npm 发布快照，若存在）与 catalog/problems.json 一致；
//   4. SKILL.md 接入片段里的 leitu 库版本 == 根 pom 的 <version>（版本真源唯一）。
//
// 为什么第 4 条在这里：**leitu 库版本才是有语义的那条线**（Maven，真源 = 根 pom 的 <version>），
// AI 读 SKILL.md 才知道「新项目该引哪个版本」。允许字面量有第二处，但必须有机制强制相等——
// 靠人记得同步等于没有同步（SKILL.md 里曾写着 0.2.0，而库已发到 0.2.1，没人发现）。
//
// 不在此列的是 **npm CLI 的版本**：CLI 是无状态只读工具，版本不承载兼容性承诺，
// 用的人一律 `npx leitu-cli@latest`，与库版本各走各的 tag、各发各的——不要把它也绑进来。
//
// 本地/CI 通用（零依赖，只要 node）：node tools/cli/check-skill.mjs
// 反向自测：改 SKILL.md 的路由 → 红在覆盖；改 SKILL.md 的 <version> → 红在版本。
// 锚点未命中一律**报错退出**，不静默通过——守门人「找不到就当通过」等于把校验关掉。

import { readFileSync, existsSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const errors = [];

// 路径类假设一律「向上找标志文件」，不固定相对层级——脚本挪个位置就失效的写法已经踩过。
function findUp(start, marker) {
  let dir = start;
  for (let i = 0; i < 8; i += 1) {
    if (existsSync(join(dir, marker))) return dir;
    const parent = resolve(dir, "..");
    if (parent === dir) break;
    dir = parent;
  }
  return null;
}

const ROOT = findUp(HERE, "catalog") ?? findUp(HERE, "pom.xml");
if (!ROOT) {
  console.error("SKILL 守门失败：\n  - 从 " + HERE + " 向上找不到仓库根（catalog/ 或 pom.xml）");
  process.exit(1);
}

const catalogPath = join(ROOT, "catalog", "problems.json");
const skillPath = join(ROOT, "skills", "leitu", "SKILL.md");
const pomPath = join(ROOT, "pom.xml");
for (const p of [catalogPath, skillPath, pomPath]) {
  if (!existsSync(p)) {
    console.error(`SKILL 守门失败：\n  - 缺文件 ${p}`);
    process.exit(1);
  }
}

const catalog = JSON.parse(readFileSync(catalogPath, "utf8"));
const skill = readFileSync(skillPath, "utf8");
const pom = readFileSync(pomPath, "utf8");

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
  const b = readFileSync(catalogPath, "utf8");
  if (a !== b) {
    errors.push(
      "tools/cli/catalog-snapshot.json 与 catalog/problems.json 不一致——执行 prepack（cp ../../catalog/problems.json catalog-snapshot.json）后重试"
    );
  }
}

// 4. SKILL.md 里的库版本 == 根 pom 的 <version>
// 锚点：父 POM 自身坐标块里的 version。不用「第一个 <version>」这类位置假设——位置会随文档增删漂移。
const VERSION_ANCHOR = (text) =>
  text.match(/<artifactId>\s*leitu-parent\s*<\/artifactId>\s*<version>([^<]+)<\/version>/);
const SEMVER = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/;

const pomHit = VERSION_ANCHOR(pom);
const skillHit = VERSION_ANCHOR(skill);
if (!pomHit) {
  errors.push(
    "未能从根 pom 提取版本：锚点 <artifactId>leitu-parent</artifactId> 后紧跟的 <version> 没命中。\n" +
      "    若父 POM 改了 artifactId，请同步本脚本的锚点——否则这条守卫形同关闭，按失败处理。"
  );
} else if (!SEMVER.test(pomHit[1].trim())) {
  errors.push(`从 pom 提取到的版本 '${pomHit[1].trim()}' 不像语义版本——锚点可能抓错了行`);
}
if (!skillHit) {
  errors.push(
    "未能从 SKILL.md 提取版本：接入片段里缺少 <artifactId>leitu-parent</artifactId> 紧跟 <version>…</version> 的写法。\n" +
      "    （AI 就是照着这段建新项目的，锚点必须存在。）"
  );
} else if (!SEMVER.test(skillHit[1].trim())) {
  errors.push(`从 SKILL.md 提取到的版本 '${skillHit[1].trim()}' 不像语义版本——锚点可能抓错了行`);
}
if (pomHit && skillHit && pomHit[1].trim() !== skillHit[1].trim()) {
  errors.push(
    `SKILL.md 的 leitu 版本落后/分叉：根 pom 是 ${pomHit[1].trim()}，SKILL.md 写的是 ${skillHit[1].trim()}。\n` +
      `    修复：把 SKILL.md 接入片段的 <version> 改成 ${pomHit[1].trim()}（AI 靠它决定新项目引哪个版本）。`
  );
}

if (errors.length) {
  console.error("SKILL/catalog 守门失败：");
  for (const e of errors) console.error("  - " + e);
  process.exit(1);
}
console.log(
  `SKILL/catalog 守门通过：${new Set(pathRefs).size} 个路径引用有效，` +
    `${catalog.entries.filter((x) => x.status === "answered").length} 条 answered 全覆盖，` +
    `SKILL.md 版本 == 根 pom 版本（${pomHit[1].trim()}）。`
);
