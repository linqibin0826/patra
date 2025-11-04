# Claude Code Hooks

Patra 项目的自定义 hooks,用于自动激活技能和执行项目检查。

## 🎯 技能激活 Hook

**文件**: `skill-activation-prompt.sh`

在用户提交 prompt 时自动检测是否需要激活特定技能。

**依赖**: bash + jq

**测试**:
```bash
export CLAUDE_PROJECT_DIR="$PWD"
echo '{"prompt":"create backend controller"}' | ./.claude/hooks/skill-activation-prompt.sh
```

## 📦 其他 Hooks

- **mark-java-changes.sh** - 标记 Java 文件修改 (PostToolUse)
- **auto-format-java.sh** - 自动格式化 Java 代码 (PostToolUse)
- **maven-compile-check.sh** - 检查编译错误 (Stop)
- **trigger-build-resolver-java.sh** - 自动修复编译错误 (Stop)

## 🔧 维护

技能规则: `.claude/skills/skill-rules.json`
调试模式: `claude --debug`

详细配置说明见 `CONFIG.md`
