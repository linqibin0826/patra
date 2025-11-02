# Hooks（Papertrace 版本）

用于实现技能自动激活、文件跟踪和 Maven 编译验证的 Claude Code hooks，适用于 Java/Spring Boot 项目。

---

## 什么是 Hooks？

Hooks 是在 Claude 工作流程的特定点运行的脚本：
- **UserPromptSubmit**: 当用户提交提示时
- **PreToolUse**: 工具执行前
- **PostToolUse**: 工具完成后
- **Stop**: 当用户请求停止时

**关键洞察**：Hooks 可以修改提示、阻止操作和跟踪状态 - 实现 Claude 单独无法做到的功能。

---

## 必要的 Hooks（从这里开始）

### skill-activation-prompt (UserPromptSubmit)

**目的**：根据用户提示和文件上下文自动建议相关的技能

**工作原理**：
1. 读取 `skill-rules.json`
2. 将用户提示与触发器模式匹配
3. 检查用户正在使用哪些文件
4. 将技能建议注入到 Claude 的上下文中

**为什么这很重要**：这是使技能自动激活的关键 hook。

**集成**：
```bash
# 复制两个文件
cp skill-activation-prompt.sh your-project/.claude/hooks/
cp skill-activation-prompt.ts your-project/.claude/hooks/

# 设置为可执行
chmod +x your-project/.claude/hooks/skill-activation-prompt.sh

# 安装依赖
cd your-project/.claude/hooks
npm install
```

**添加到 settings.json**：
```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/skill-activation-prompt.sh"
          }
        ]
      }
    ]
  }
}
```

**自定义**：✅ 无需自定义 - 自动读取 skill-rules.json

---

### post-tool-use-tracker (PostToolUse)

**目的**：跟踪文件更改以在会话间维护上下文

**工作原理**：
1. 监控 Edit/Write/MultiEdit 工具调用
2. 记录哪些文件被修改
3. 为上下文管理创建缓存
4. 自动检测项目结构（服务、模块、包等）

**为什么这很重要**：帮助 Claude 理解代码库的哪些部分处于活跃状态。

**集成**：
```bash
# 复制文件
cp post-tool-use-tracker.sh your-project/.claude/hooks/

# 设置为可执行
chmod +x your-project/.claude/hooks/post-tool-use-tracker.sh
```

**添加到 settings.json**：
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/post-tool-use-tracker.sh"
          }
        ]
      }
    ]
  }
}
```

**自定义**：✅ 无需自定义 - 自动检测结构

---

## Java 特定的 Hooks（推荐用于 Papertrace）

### maven-compile-check (Stop)

**目的**：当用户停止 Claude 时进行快速 Maven 编译检查

**工作原理**：
1. 运行 `mvn -T 1C compile -q -DskipTests`
2. 检测编译错误
3. 显示错误摘要
4. 如果编译失败，创建标记文件

**为什么这很有用**：在提交代码之前及早发现编译错误

**⚠️ 注意**：此 hook 运行 `mvn compile`，对于大型项目可能需要几秒钟。

**集成**：
```bash
# 复制文件
cp maven-compile-check.sh your-project/.claude/hooks/

# 设置为可执行
chmod +x your-project/.claude/hooks/maven-compile-check.sh
```

**添加到 settings.json**：
```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          }
        ]
      }
    ]
  }
}
```

**要求**：
- Maven (`mvn`) 必须在 PATH 中
- `pom.xml` 必须存在于项目根目录

**自定义**：✅ 无需自定义 - 适用于任何 Maven 项目

---

### trigger-build-resolver-java (Stop)

**目的**：当 Maven 编译失败时建议使用自动错误解析器代理

**工作原理**：
1. 检查 `.last-compile-failed` 标记文件（由 maven-compile-check.sh 创建）
2. 显示友好的提示，建议使用自动错误解析器代理
3. 清理标记文件

**为什么这很有用**：在 AI 协助下自动提示用户修复编译错误

**依赖关系**：需要 `maven-compile-check.sh` 首先运行

**集成**：
```bash
# 复制文件
cp trigger-build-resolver-java.sh your-project/.claude/hooks/

# 设置为可执行
chmod +x your-project/.claude/hooks/trigger-build-resolver-java.sh
```

**添加到 settings.json（在 maven-compile-check 之后）**：
```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          },
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/trigger-build-resolver-java.sh"
          }
        ]
      }
    ]
  }
}
```

**自定义**：✅ 无需自定义

---

## 完整的 Papertrace 配置

**推荐的 Papertrace 项目 settings.json**：

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/skill-activation-prompt.sh"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/post-tool-use-tracker.sh"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/maven-compile-check.sh"
          },
          {
            "type": "command",
            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/trigger-build-resolver-java.sh"
          }
        ]
      }
    ]
  }
}
```

---

## Hook 执行顺序

### 用户提交提示时：
1. `skill-activation-prompt.sh` → 分析提示并注入技能建议

### 文件编辑后：
1. `post-tool-use-tracker.sh` → 记录文件更改

### 停止时：
1. `maven-compile-check.sh` → 运行 Maven 编译
2. `trigger-build-resolver-java.sh` → 如果编译失败，建议使用代理

---

## 故障排查

### Hook 未执行

**检查权限**：
```bash
ls -la .claude/hooks/*.sh | grep rwx
```

所有 `.sh` 文件都应该有 `x`（可执行）权限。

**修复**：
```bash
chmod +x .claude/hooks/*.sh
```

---

### skill-activation-prompt 无法工作

**检查依赖**：
```bash
cd .claude/hooks
npm install
```

**验证 skill-rules.json 存在**：
```bash
ls -la .claude/skills/skill-rules.json
```

---

### maven-compile-check 过慢

**选项 1：禁用多线程**
编辑 `maven-compile-check.sh` 第 36 行：
```bash
# 从以下改为：
mvn -T 1C compile -q -DskipTests

# 改为：
mvn compile -q -DskipTests
```

**选项 2：跳过此 hook**
从 `settings.json` 的 Stop hooks 部分移除。

---

### Maven 未找到

**错误**：`mvn: command not found`

**解决方案**：确保 Maven 已安装并在 PATH 中：
```bash
which mvn
mvn --version
```

如果需要，安装 Maven：
```bash
# macOS
brew install maven

# Linux (Ubuntu/Debian)
sudo apt-get install maven
```

---

## 对于 Claude Code

**为用户设置 hooks 时**：

1. **首先阅读 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)**
2. **始终从两个必要的 hooks 开始**（skill-activation-prompt + post-tool-use-tracker）
3. **Java 项目：添加 Maven hooks** 用于编译检查
4. **设置后验证**：
   ```bash
   ls -la .claude/hooks/*.sh | grep rwx
   cd .claude/hooks && npm install
   ```

**有问题？** 查看 [CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md)
