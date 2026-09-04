#!/usr/bin/env bash
# 夜之城快递员 - 一键构建脚本 (Linux / macOS)
# 用法:  cd NightCityCourier ; ./build.sh
# 可选:  ./build.sh --run     编译后直接启动游戏
#        ./build.sh --test    编译并运行单元测试

set -euo pipefail

RUN=false
TEST=false
for arg in "$@"; do
  case "$arg" in
    --run)  RUN=true ;;
    --test) TEST=true ;;
    *) echo "[错误] 未知参数: $arg" >&2; exit 1 ;;
  esac
done

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/com/moji/NightCityCourier"
OUT_DIR="$PROJECT_DIR/out"
TEST_SRC_DIR="$PROJECT_DIR/test/com/moji/NightCityCourier"
TEST_OUT_DIR="$PROJECT_DIR/test-out"

# ── 依赖检查 ──
for tool in javac java; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "[错误] 未找到 $tool，请先安装 JDK 17 或更高版本。" >&2
    exit 1
  fi
done
echo "[环境] $(javac -version)"

# ── 编译 ──
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "[编译] 主程序..."
javac -encoding UTF-8 -d "$OUT_DIR" "$SRC_DIR"/*.java

if [ "$TEST" = true ]; then
  rm -rf "$TEST_OUT_DIR"
  mkdir -p "$TEST_OUT_DIR"
  echo "[编译] 单元测试..."
  javac -encoding UTF-8 -d "$TEST_OUT_DIR" "$SRC_DIR"/*.java "$TEST_SRC_DIR"/*.java
  echo "[运行] 单元测试..."
  java -cp "$TEST_OUT_DIR" com.moji.NightCityCourier.CoreSystemsTest
  echo "[完成] 单元测试全部通过 ✅"
fi

echo "[完成] 编译产物位于 out/ 目录"

if [ "$RUN" = true ]; then
  echo "[启动] 游戏..."
  java -cp "$OUT_DIR" com.moji.NightCityCourier.Main
fi
