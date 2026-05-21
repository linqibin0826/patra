#!/usr/bin/env bash
# 二分查找：找到哪个测试类污染了文件系统或共享状态
#
# 用途：测试套件跑完后，发现仓库里多了不该有的文件（如 .cache、临时
#       数据库文件、未清理的 worktree），但不知道是哪个测试类造成的。
#
# 用法：./find-polluter.sh <污染路径> <Gradle 测试类匹配模式>
# 示例：./find-polluter.sh '.cache/test-tmp' '*PublicationServiceTest*'
#       ./find-polluter.sh '/tmp/patra-leak' 'com.patra.registry.*Test'
#
# 工作原理：
#   1. 用 ./gradlew test --tests 逐批运行测试类
#   2. 每跑完一批检查"污染路径"是否出现
#   3. 一旦出现，输出最后一批中的测试类清单
#
# 注意：本脚本假设你已在 patra-api 项目根目录下；测试运行用 ./gradlew

set -euo pipefail

if [ $# -ne 2 ]; then
  echo "用法：$0 <污染路径> <Gradle 测试类匹配模式>"
  echo "示例：$0 '.cache/test-tmp' '*PublicationServiceTest*'"
  echo "     $0 '/tmp/patra-leak' 'com.patra.registry.*Test'"
  exit 1
fi

POLLUTION_CHECK="$1"
TEST_PATTERN="$2"

echo "🔍 查找会创建以下路径的测试：$POLLUTION_CHECK"
echo "Gradle test 匹配模式：$TEST_PATTERN"
echo ""

# 1. 列出所有匹配的测试类（用 Gradle 内省 + grep）
#    这里假设单元测试位于 src/test/java，集成测试位于 src/test/java 或 src/integrationTest/java
TEST_CLASSES=$(find . -path '*/src/test/java/*Test.java' -o -path '*/src/integrationTest/java/*Test.java' 2>/dev/null \
  | sed -E 's|.*/src/(test|integrationTest)/java/||; s|\.java$||; s|/|.|g' \
  | grep -E "${TEST_PATTERN//\*/.*}" \
  | sort -u)

TOTAL=$(echo "$TEST_CLASSES" | grep -c . || true)

if [ "$TOTAL" -eq 0 ]; then
  echo "⚠️  没找到匹配模式 '$TEST_PATTERN' 的测试类"
  exit 1
fi

echo "找到 $TOTAL 个测试类"
echo ""

COUNT=0
for CLASS in $TEST_CLASSES; do
  COUNT=$((COUNT + 1))

  # 已经污染了 → 说明前一个测试就是污染者，但脚本只能定位到批次
  if [ -e "$POLLUTION_CHECK" ]; then
    echo "⚠️  在测试 $COUNT/$TOTAL 之前污染就已存在"
    echo "   先手动清掉 $POLLUTION_CHECK 再跑本脚本"
    exit 2
  fi

  echo "[$COUNT/$TOTAL] 运行：$CLASS"

  # 跑单个测试类（--no-daemon 避免 Gradle daemon 缓存状态）
  ./gradlew test --tests "$CLASS" --no-daemon > /dev/null 2>&1 || true

  # 污染出现 → 找到了
  if [ -e "$POLLUTION_CHECK" ]; then
    echo ""
    echo "🎯 找到污染者！"
    echo "   测试类：$CLASS"
    echo "   创建了：$POLLUTION_CHECK"
    echo ""
    echo "污染详情："
    ls -la "$POLLUTION_CHECK"
    echo ""
    echo "调查命令："
    echo "  ./gradlew test --tests \"$CLASS\" --no-daemon -i   # 详细日志重跑"
    echo "  grep -rn '$POLLUTION_CHECK' \$(find . -path '*/src/test/java/*Test.java' \\"
    echo "                                    | xargs grep -l \"\${CLASS##*.}\")"
    exit 0
  fi
done

echo ""
echo "✅ 没找到污染者——所有测试干净！"
echo "（如果你确信存在污染，检查：是不是 build 阶段产生的？是不是 application*.yml 配置的？）"
exit 0
