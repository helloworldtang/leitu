#!/usr/bin/env node
// leitu CLI——给人和 AI 的标准答案目录查询。零依赖、只读。
// 设计判据（Agent 友好）：
//   - 全局 --json：机器可读输出，skill 里约定"给 AI 的调用总带 --json"；
//   - 一次性完整输出（非流式）：Agent 经管道消费，碎片化输出无法解析；
//   - explain 返回 next-action（文档/金样本的可达地址），而不是让 agent 猜路径。

import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const GITHUB = "https://github.com/helloworldtang/leitu/blob/main";
const HERE = dirname(fileURLToPath(import.meta.url));

function loadCatalog(override) {
  // 数据源优先级：--catalog 显式指定 > 仓库内 catalog（同仓开发时，永不漂移）> 包内快照（npm 安装时）
  const candidates = [
    override,
    join(HERE, "..", "..", "catalog", "problems.json"),
    join(HERE, "catalog-snapshot.json"),
  ].filter(Boolean);
  for (const p of candidates) {
    if (existsSync(p)) {
      return { data: JSON.parse(readFileSync(p, "utf8")), path: resolve(p) };
    }
  }
  fail("找不到 catalog 数据源。可用 --catalog <path> 指定 catalog/problems.json。");
}

function fail(msg, code = 1) {
  console.error(JSON.stringify({ error: msg }));
  process.exit(code);
}

function entryView(e, root) {
  const rel = (p) => (p ? join(root, p) : null);
  return {
    id: e.id,
    problem: e.problem,
    status: e.status,
    required: e.required,
    stability: e.stability,
    since: e.since,
    depends_on: e.depends_on ?? [],
    sub_questions: e.sub_questions ?? [],
    doc: {
      repo_path: e.links?.doc ?? null,
      url: e.links?.doc ? `${GITHUB}/${e.links.doc}` : null,
      local_exists: e.links?.doc ? existsSync(rel(e.links.doc)) : null,
    },
    example: {
      repo_path: e.links?.example ?? null,
      url: e.links?.example ? `${GITHUB}/${e.links.example}` : null,
      local_exists: e.links?.example ? existsSync(rel(e.links.example)) : null,
    },
    maven: {
      bom: "cn.youhuale:leitu-parent:0.2.0 (scope=import)",
      starter: "cn.youhuale:leitu-spring-boot-starter",
    },
  };
}

function repoRoot() {
  // 仓库根 = tools/cli 往上两级（npm 安装时不存在，local_exists 相应返回 null）
  return join(HERE, "..", "..");
}

const args = process.argv.slice(2);
const asJson = args.includes("--json");
const all = args.includes("--all");
const catalogOverride = args.includes("--catalog")
  ? args[args.indexOf("--catalog") + 1]
  : null;
const [cmd, ...rest] = args.filter(
  (a) => !a.startsWith("--") && a !== catalogOverride
);

const { data, path } = loadCatalog(catalogOverride);
const root = path.includes(`${join("tools", "cli")}`) ? repoRoot() : dirname(path);

if (cmd === "list" || cmd === undefined) {
  const entries = data.entries.filter((e) => all || e.status === "answered");
  if (asJson) {
    console.log(
      JSON.stringify(
        {
          project: data.project,
          catalog: path,
          note: "status: answered=五件套齐且有实现；open=已立目未答。不写版本号：接入方式见 maven 字段。",
          entries: entries.map((e) => ({
            id: e.id,
            problem: e.problem,
            status: e.status,
            required: e.required,
            since: e.since,
          })),
        },
        null,
        2
      )
    );
  } else {
    console.log(`leitu 标准答案目录（${path}）\n`);
    for (const e of entries) {
      console.log(`  ${e.id.padEnd(22)} ${e.status.padEnd(9)} ${e.problem}`);
    }
    if (!all) console.log("\n（仅 answered；--all 查看含 open 在内的全部条目）");
  }
  process.exit(0);
}

if (cmd === "explain") {
  const id = rest[0];
  const e = data.entries.find((x) => x.id === id);
  if (!e) {
    fail(`目录中没有「${id}」。先 npx leitu list 查看全部条目。`);
  }
  const view = entryView(e, root);
  console.log(asJson ? JSON.stringify(view, null, 2) : renderText(view));
  process.exit(0);
}

fail(`未知命令「${cmd ?? ""}」。可用：list [--all] [--json] / explain <id> [--json]`);

function renderText(v) {
  const lines = [
    `${v.id}（${v.status}${v.since ? `，since ${v.since}` : ""}）`,
    `问题：${v.problem}`,
    "",
    `细读   ${v.doc.repo_path}`,
    `       ${v.doc.url}`,
    `金样本 ${v.example.repo_path}`,
    `       ${v.example.url}`,
    "",
    `接入   ${v.maven.bom}`,
    `       ${v.maven.starter}`,
  ];
  if (v.sub_questions.length) {
    lines.push("", "子问题：");
    for (const s of v.sub_questions) lines.push(`  - ${s}`);
  }
  if (v.depends_on.length) lines.push("", `依赖答案：${v.depends_on.join(", ")}`);
  return lines.join("\n");
}
