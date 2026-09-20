#!/usr/bin/env bash
# 绘世 Imagine 一键发版脚本
#
# 用法:
#   bash tools/release.sh <版本名> "<更新说明文件>" [--install]
# 示例:
#   bash tools/release.sh 1.2 dist/RELEASE_NOTES.md
#   bash tools/release.sh 1.2 dist/RELEASE_NOTES.md --install
#
# 做的事:
#   1. 校验工作区干净、版本号未发布过
#   2. 同步 versionName / versionCode（versionCode 自增）
#   3. 构建 + 跑全部单元测试（测试不过直接中止）
#   4. 提交并推送源码
#   5. 创建 GitHub Release 并上传 APK
#   6. 可选：覆盖安装到本机（--install）
#
# 注意: 需要已登录的 gh CLI（gh auth status 应显示已登录）。

set -uo pipefail

VERSION="${1:-}"
NOTES="${2:-}"
DO_INSTALL="${3:-}"

if [[ -z "$VERSION" || -z "$NOTES" ]]; then
  echo "用法: bash tools/release.sh <版本名> \"<更新说明文件>\" [--install]"
  echo "示例: bash tools/release.sh 1.2 dist/RELEASE_NOTES.md"
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

TAG="v$VERSION"
GRADLE_PROPS="app/build.gradle.kts"

step() { echo; echo "==> $*"; }
die()  { echo "!! $*" >&2; exit 1; }

# ---------- 1. 前置校验 ----------
step "前置校验"

if ! gh auth status >/dev/null 2>&1; then
  die "gh 未登录，请先执行 gh auth login"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "工作区有未提交改动："
  git status --short
  die "请先提交或清理后再发版（脚本自身会做版本号提交）"
fi

if gh release view "$TAG" >/dev/null 2>&1; then
  die "版本 $TAG 已存在，请换一个版本号"
fi

if [[ ! -f "$NOTES" ]]; then
  die "更新说明文件不存在：$NOTES"
fi

CURRENT_VERSION="$(grep -oP 'versionName = "\K[^"]+' "$GRADLE_PROPS" | head -n1)"
CURRENT_CODE="$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_PROPS" | head -n1)"
NEW_CODE=$((CURRENT_CODE + 1))

echo "当前版本 : $CURRENT_VERSION (code $CURRENT_CODE)"
echo "发布版本 : $VERSION (code $NEW_CODE)"
echo "标签     : $TAG"
echo "说明文件 : $NOTES"

if [[ "$VERSION" == "$CURRENT_VERSION" ]]; then
  die "新版本号与当前 versionName 相同（$VERSION）——必须递增，否则应用内更新检测不到"
fi

# ---------- 2. 版本号 ----------
step "写入版本号"
python3 - "$GRADLE_PROPS" "$VERSION" "$NEW_CODE" <<'PY'
import re, sys
path, version, code = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path).read()
s2 = re.sub(r'versionCode = \d+', f'versionCode = {code}', s, count=1)
s2 = re.sub(r'versionName = "[^"]*"', f'versionName = "{version}"', s2, count=1)
assert s2 != s, 'version fields not found'
open(path, 'w').write(s2)
print(f'versionCode -> {code}, versionName -> {version}')
PY
[[ $? -eq 0 ]] || die "版本号写入失败"

# ---------- 3. 构建与测试 ----------
step "构建 + 单元测试"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ANDROID_HOME=/root/Android \
  bash gradlew -p "$ROOT" :app:assembleDebug :app:testDebugUnitTest \
  --console=plain --max-workers=2 > /tmp/release-build.log 2>&1
BUILD_EXIT=$?
tail -n 4 /tmp/release-build.log

if [[ $BUILD_EXIT -ne 0 ]]; then
  grep -nE '^e: |FAILED|error:' /tmp/release-build.log | head -n 20
  die "构建失败，已中止（版本号改动未提交，可用 git checkout 回退）"
fi

python3 - <<'PY' || die "单元测试存在失败"
import glob, sys, xml.etree.ElementTree as ET
total = fails = errs = skip = 0
for f in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r = ET.parse(f).getroot()
    total += int(r.get('tests', 0)); fails += int(r.get('failures', 0))
    errs += int(r.get('errors', 0)); skip += int(r.get('skipped', 0))
print(f'单元测试: 共 {total} 项, 失败 {fails}, 错误 {errs}, 跳过 {skip}')
sys.exit(1 if (fails or errs) else 0)
PY

# ---------- 4. 打包 ----------
step "收集产物"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="dist/imagine-$VERSION.apk"
mkdir -p dist
cp "$APK_SRC" "$APK_OUT" || die "APK 复制失败"
SHA="$(sha256sum "$APK_OUT" | cut -d' ' -f1)"
BYTES="$(stat -c%s "$APK_OUT")"
echo "APK    : $APK_OUT"
echo "大小   : $BYTES 字节"
echo "SHA256 : $SHA"

# ---------- 5. 提交推送 ----------
step "提交并推送源码"
git add -A
git -c user.name="${GIT_AUTHOR_NAME:-changeweiyang}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-noreply@localhost}" \
    commit -q -m "发布 v$VERSION：版本号与 Release 产物" || echo "(无改动可提交，继续)"
git push origin HEAD 2>&1 | tail -n 2
echo "已推送: $(git log --oneline -1)"

# ---------- 6. Release ----------
step "创建 GitHub Release"
gh release create "$TAG" "$APK_OUT" \
  --title "绘世 Imagine v$VERSION" \
  --notes-file "$NOTES" 2>&1 | tail -n 2

echo
echo "================ 发版完成 ================"
echo "版本     : $TAG"
echo "Release  : https://github.com/lzhhhhc/imagine/releases/tag/$TAG"
echo "下载链接 : https://github.com/lzhhhhc/imagine/releases/download/$TAG/imagine-$VERSION.apk"
echo "SHA256   : $SHA"
echo
echo "验证 latest 接口（应用检查更新用的就是这个）："
curl -sS -H 'Accept: application/vnd.github+json' \
  -H 'User-Agent: imagine-android-updater' \
  https://api.github.com/repos/lzhhhhc/imagine/releases/latest \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('  latest tag:', d.get('tag_name'));[print('  asset:',a['name'],a['size'],'bytes') for a in d.get('assets',[])]"

# ---------- 7. 可选安装 ----------
if [[ "$DO_INSTALL" == "--install" ]]; then
  step "覆盖安装到本机"
  cp "$APK_OUT" /data/local/tmp/imagine-release.apk
  chmod 644 /data/local/tmp/imagine-release.apk
  pm install -r /data/local/tmp/imagine-release.apk 2>&1 | tail -n 2
  echo "已装版本:"
  dumpsys package com.lo.imagine | grep -E 'versionName=|versionCode=' | head -n 2
fi

exit 0