#!/usr/bin/env bash
# 发布前的版本闸门：把"打错 tag / 带着 SNAPSHOT 发版"这两件事拦在执行 deploy 之前。
#
# 两道检查：
#   1. pom 版本不得是 SNAPSHOT——SNAPSHOT 上 Central 既不被接受，也会污染 already-released 的版本空间；
#   2. tag 触发时，tag 必须与 pom 版本一致（允许 v 前缀：v0.2.0 ↔ 0.2.0）。
#      打错位——一发上 Central 就无法撤回，版本号也不能复用。
#
# 用法：scripts/check-release-version.sh [tag]
#   有 tag 参数：同时校验 tag 与 pom 版本一致；无参数：只校验非 SNAPSHOT（本地自检用）。
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION=$(./mvnw -q -B -ntp -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null | tail -n 1)
echo "pom version: ${VERSION}"

case "${VERSION}" in
  *-SNAPSHOT)
    echo "::error::pom 版本仍是 SNAPSHOT（${VERSION}）——发版前先把 -SNAPSHOT 去掉"
    echo "::error::修复：./mvnw versions:set -DnewVersion=0.2.0 -DgenerateBackupPoms=false，提交后再打 tag"
    exit 1
    ;;
esac

TAG="${1:-}"
if [[ -n "${TAG}" ]]; then
  EXPECTED="${TAG#v}"
  if [[ "${VERSION}" != "${EXPECTED}" ]]; then
    echo "::error::tag(${TAG}) 与 pom 版本(${VERSION}) 不一致——expected ${EXPECTED}"
    echo "::error::修复二选一：① 改 pom 版本并重新提交；② 删掉错打的 tag 重新打（Central 上的组件不可撤回）"
    exit 1
  fi
  echo "tag(${TAG}) 与 pom 版本(${VERSION}) 一致"
fi

echo "版本闸门通过"
