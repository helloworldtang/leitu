#!/usr/bin/env node
// 发布前置自检 —— 把「发了才发现」的问题挡在上传之前。
//
// 每一条都对应一次真实翻车：
//   1. tag 与 package.json 版本不一致 → 发出错版本，或 403（该版本已存在）；
//   2. bin 值带 './' 前缀 → npm 判 invalid 并**静默剥掉 bin**，用户装了没有命令
//      （v0.2.1 首发踩过；`npm pack --dry-run` 看不出来，因为它只列文件不校验 manifest）；
//   3. files 白名单漏了入口 / prepack 产物的源不存在 → 包能发，装上跑不了；
//   4. 该版本已在 registry → npm 403 "cannot publish over previously published versions"。
//
// 用法：
//   node preflight.mjs                      # 本地：自动读 GITHUB_REF_NAME（若有）
//   GITHUB_REF_NAME=cli-v0.2.2 node preflight.mjs
//   node preflight.mjs --skip-registry      # 不联网
//   node preflight.mjs --allow-published    # 已发布只警告（dry run / 复跑）

import { readFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const skipRegistry = args.includes('--skip-registry');
const allowPublished = args.includes('--allow-published');
const ref = process.env.GITHUB_REF_NAME ?? args.find((a) => a.startsWith('--tag='))?.slice(6) ?? '';

const pkg = JSON.parse(readFileSync(resolve(HERE, 'package.json'), 'utf8'));
const problems = [];
const notes = [];
const strip = (p) => String(p).replace(/^\.\//, '');

// 1. tag ↔ package.json 版本
const m = /^cli-v(.+)$/.exec(ref);
if (m) {
  if (m[1] !== pkg.version) {
    problems.push(`tag ${ref} 隐含版本 ${m[1]}，但 package.json 是 ${pkg.version}——先改 package.json 再打 tag`);
  } else {
    notes.push(`tag 与 package.json 版本一致：${pkg.version}`);
  }
} else if (ref) {
  notes.push(`ref='${ref}' 不是 cli-v* tag——跳过版本一致性断言（dispatch / 本地运行属正常）`);
} else {
  notes.push('未提供 GITHUB_REF_NAME——跳过版本一致性断言');
}

// 2. bin 字段有效性
const binEntries = Object.entries(pkg.bin ?? {});
if (binEntries.length === 0) {
  problems.push('package.json 缺 bin 字段——装上不会有任何命令');
} else {
  notes.push(`包名 ${pkg.name}，命令名 ${binEntries.map(([n]) => n).join(', ')}（两者可以不同）`);
  for (const [name, target] of binEntries) {
    if (typeof target !== 'string' || !target) {
      problems.push(`bin.${name} 不是有效路径`);
      continue;
    }
    if (target.startsWith('./')) {
      problems.push(`bin.${name}='${target}' 带 './' 前缀——npm 会判 invalid 并静默剥掉 bin（v0.2.1 首发踩过）`);
    }
    const abs = resolve(HERE, strip(target));
    if (!existsSync(abs)) {
      problems.push(`bin.${name} → ${target} 文件不存在`);
    } else if (!readFileSync(abs, 'utf8').startsWith('#!')) {
      notes.push(`bin.${name} 无 shebang——仍能跑（npm 用 node 调），但不保险`);
    }
  }
}

// 3. files 白名单
const files = pkg.files ?? [];
if (files.length === 0) {
  problems.push('package.json 缺 files 白名单——会把仓库垃圾一起发出去');
}
for (const [, target] of binEntries) {
  const t = strip(target);
  if (t && !files.includes(t)) {
    problems.push(`files 未包含入口 ${t}——装上没有命令`);
  }
}
if (files.includes('catalog-snapshot.json')) {
  const src = resolve(HERE, '../../catalog/problems.json');
  if (!existsSync(src)) {
    problems.push('catalog-snapshot.json 由 prepack 生成，但源 ../../catalog/problems.json 不存在');
  } else {
    notes.push('catalog-snapshot.json ← catalog/problems.json（prepack 生成，单一数据源）');
  }
}

// 4. tarball 实际内容（走 npm 自己的打包逻辑，不是我们猜的）
try {
  const out = execFileSync('npm', ['pack', '--dry-run', '--json'], {
    cwd: HERE,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const parsed = JSON.parse(out);
  const entry = Array.isArray(parsed) ? parsed[0] : parsed;
  const packed = new Set((entry.files ?? []).map((f) => f.path));
  for (const [, target] of binEntries) {
    const t = strip(target);
    if (t && !packed.has(t)) problems.push(`tarball 里没有 ${t}——入口被 files 白名单挡掉了`);
  }
  notes.push(`tarball：${packed.size} 个文件 / ${(entry.size / 1024).toFixed(1)} kB`);
} catch (e) {
  problems.push(`npm pack --dry-run 失败：${String(e.message).split('\n')[0]}`);
}

// 5. registry 版本占用
if (skipRegistry) {
  notes.push('--skip-registry：未查 registry');
} else {
  const url = `https://registry.npmjs.org/${pkg.name}/${pkg.version}`;
  try {
    const res = await fetch(url);
    if (res.status === 200) {
      const line = `${pkg.name}@${pkg.version} 已存在于 registry——npm 会 403，先升版本号`;
      if (allowPublished) notes.push(`${line}（--allow-published：降级为提示）`);
      else problems.push(line);
    } else if (res.status === 404) {
      notes.push(`${pkg.name}@${pkg.version} 未被占用 ✅`);
    } else {
      notes.push(`registry 查询返回 ${res.status}——跳过占用检查`);
    }
  } catch (e) {
    notes.push(`registry 查询失败（${String(e.message)}）——跳过占用检查`);
  }
}

console.log(`发布前置自检：${pkg.name}@${pkg.version}（ref='${ref || '-'}'）`);
for (const n of notes) console.log(`  · ${n}`);
if (problems.length) {
  console.error('\n自检未通过：');
  for (const p of problems) console.error(`  ✗ ${p}`);
  process.exit(1);
}
console.log('\n自检通过 ✅');
