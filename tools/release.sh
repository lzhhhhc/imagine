#!/usr/bin/env bash
# 绘世 Imagine 一键发版脚本
#
# 用法:
#   bash tools/release.sh <版本名> "<更新说明文件>" [--install|--local-only]
# 示例:
#   bash tools/release.sh 1.2 dist/RELEASE_NOTES.md
#   bash tools/release.sh 1.2 dist/RELEASE_NOTES.md --install
#   bash tools/release.sh 1.2 dist/RELEASE_NOTES.md --local-only  # 明确只构建，不访问 GitHub
#
# 做的事:
#   1. 校验工作区干净、版本号未发布过
#   2. 同步 versionName / versionCode（versionCode 自增）
#   3. 构建 + 跑全部单元测试（测试不过直接中止）
#   4. 提交并推送源码
#   5. 创建 GitHub Release 并上传 APK
#   6. 可选：导出到共享目录并交给 Android shell 安装（--install）
#
# 注意: 远端发布需要已登录的 gh CLI；--local-only 不读取或使用 GitHub 凭据。

set -uo pipefail

VERSION="${1:-}"
NOTES="${2:-}"
MODE="${3:-}"

if [[ -z "$VERSION" || -z "$NOTES" ]]; then
  echo "用法: bash tools/release.sh <版本名> \"<更新说明文件>\" [--install|--local-only]"
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

[[ $# -le 3 && ( -z "$MODE" || "$MODE" == "--install" || "$MODE" == "--local-only" ) ]] || die "未知选项：$MODE"
[[ "$VERSION" =~ ^[0-9]+(\.[0-9]+)+$ ]] || die "版本格式应为数字点分版本，例如 1.2"

if [[ "$MODE" != "--local-only" ]]; then
  gh auth status >/dev/null 2>&1 || die "gh 未登录，请使用安全的新凭据执行 gh auth login"
fi

if [[ "$MODE" != "--local-only" && -n "$(git status --porcelain)" ]]; then
  echo "工作区有未提交改动："
  git status --short
  die "请先提交或清理后再发版（脚本自身会做版本号提交）"
fi

if [[ "$MODE" != "--local-only" ]]; then
  RELEASES="$(gh release list --limit 100 --json tagName)" || die "无法读取已发布版本，已中止"
  python3 - "$TAG" "$RELEASES" <<'PY' || die "版本 $TAG 已存在或发布列表无效"
import json, sys
assert all(item['tagName'] != sys.argv[1] for item in json.loads(sys.argv[2]))
PY
fi

if [[ ! -f "$NOTES" ]]; then
  die "更新说明文件不存在：$NOTES"
fi

CURRENT_VERSION="$(grep -oP 'versionName = "\K[^"]+' "$GRADLE_PROPS" | head -n1)"
CURRENT_CODE="$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_PROPS" | head -n1)"
[[ "$CURRENT_CODE" =~ ^[0-9]+$ ]] || die "当前 versionCode 无效"
NEW_CODE=$((CURRENT_CODE + 1))

echo "当前版本 : $CURRENT_VERSION (code $CURRENT_CODE)"
echo "发布版本 : $VERSION (code $NEW_CODE)"
echo "标签     : $TAG"
echo "说明文件 : $NOTES"

python3 - "$CURRENT_VERSION" "$VERSION" "$CURRENT_CODE" <<'PY' || die "版本必须严格递增，且当前 versionCode 必须有效"
import re, sys
old, new, code = sys.argv[1:]
def version(value):
    if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+', value):
        raise ValueError('无法比较当前版本')
    parts = [int(p) for p in value.split('.')]
    while parts and parts[-1] == 0:
        parts.pop()
    return tuple(parts)
assert version(new) > version(old) and code.isdigit() and int(code) > 0
PY

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
env -u IMAGINE_COMFY_LIVE_URL -u IMAGINE_COMFY_LIVE_DIR -u IMAGINE_COMFY_LIVE_MODEL \
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
reports = glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
assert reports, '没有单元测试报告'
for f in reports:
    r = ET.parse(f).getroot()
    total += int(r.get('tests', 0)); fails += int(r.get('failures', 0))
    errs += int(r.get('errors', 0)); skip += int(r.get('skipped', 0))
print(f'单元测试: 共 {total} 项, 失败 {fails}, 错误 {errs}, 跳过 {skip}')
sys.exit(1 if (fails or errs or total <= skip) else 0)
PY

# ---------- 4. 打包 ----------
step "收集产物"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="dist/imagine-$VERSION.apk"
mkdir -p dist || die "无法创建产物目录"
cp "$APK_SRC" "$APK_OUT" || die "APK 复制失败"
SHA="$(sha256sum "$APK_OUT" | cut -d' ' -f1)"
BYTES="$(stat -c%s "$APK_OUT")"
echo "APK    : $APK_OUT"
echo "大小   : $BYTES 字节"
echo "SHA256 : $SHA"

if [[ "$MODE" == "--local-only" ]]; then
  echo "本地安装包已完成；未提交、推送或发布 GitHub Release。"
  exit 0
fi

# ---------- 5. 提交推送 ----------
step "提交并推送源码"
git add -- "$GRADLE_PROPS" || die "暂存版本号失败"
git -c user.name="${GIT_AUTHOR_NAME:-changeweiyang}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-noreply@localhost}" \
    commit -q -m "发布 v$VERSION：版本号与 Release 产物" || die "版本号提交失败"
git push origin HEAD 2>&1 | tail -n 2 || die "源码推送失败，未创建 Release"
echo "已推送: $(git log --oneline -1)"

# ---------- 6. Release ----------
step "创建 GitHub Release"
gh release create "$TAG" "$APK_OUT" \
  --title "绘世 Imagine v$VERSION" \
  --notes-file "$NOTES" --target "$(git rev-parse HEAD)" 2>&1 | tail -n 2 || die "Release 创建失败"

step "核对 Release 附件"
gh release view "$TAG" --json tagName,assets | python3 -c '
import json,sys
release=json.load(sys.stdin)
assert release["tagName"] == sys.argv[1]
assert any(a["name"] == sys.argv[2] and a["size"] == int(sys.argv[3]) for a in release["assets"])
print("Release 与 APK 文件大小已核对")
' "$TAG" "imagine-$VERSION.apk" "$BYTES" || die "Release 附件核对失败"

echo
echo "================ 发版完成 ================"
echo "版本     : $TAG"
echo "Release  : https://github.com/lzhhhhc/imagine/releases/tag/$TAG"
echo "下载链接 : https://github.com/lzhhhhc/imagine/releases/download/$TAG/imagine-$VERSION.apk"
echo "SHA256   : $SHA"

# ---------- 7. 可选安装 ----------
if [[ "$MODE" == "--install" ]]; then
  step "导出安装包到 Android 共享目录"
  SHARED_APK="/sdcard/Download/imagine-$VERSION.apk"
  cp "$APK_OUT" "$SHARED_APK" || die "复制到共享目录失败"
  echo "Ubuntu 终端不能直接调用 Android pm。请由 Android shell 执行："
  echo "cp $SHARED_APK /data/local/tmp/imagine-release.apk && chmod 644 /data/local/tmp/imagine-release.apk && pm install -r /data/local/tmp/imagine-release.apk"
  echo "安装尚未执行。安装前须核对已有应用与新 APK 的签名。"
fi

exit 0
