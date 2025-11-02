# Hooks 配置指南（Papertrace 版本）

本指南说明如何为 Java/Spring Boot 项目配置和自定义 hooks 系统。

---

## 快速入门配置

### 1. 在 .claude/settings.json 中注册 Hooks

在项目根目录创建或更新 `.claude/settings.json`：

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

### 2. 安装依赖（用于 skill-activation-prompt）

```bash
cd .claude/hooks
npm install
```

### 3. 设置执行权限

```bash
chmod +x .claude/hooks/*.sh
```

---

## 自定义选项

### 项目结构检测

默认情况下，hooks 检测这些目录模式：

**Java 服务**：`src/main/java/`、`patra-*/`、`*-service/`
**资源**：`src/main/resources/`、`src/test/java/`
**构建**：`target/`、`build/`

#### 添加自定义目录模式

编辑 `.claude/hooks/post-tool-use-tracker.sh`，函数 `detect_repo()`：

```bash
case "$repo" in
    # 在此处添加自定义服务目录
    patra-custom-service)
        echo "$repo"
        ;;
    my-module)
        echo "$repo"
        ;;
    # ... 现有模式
esac
```

---

### Maven 编译配置

#### 调整 Maven 编译命令

编辑 `.claude/hooks/maven-compile-check.sh`：

```bash
# 默认：多线程编译
mvn -T 1C compile -q -DskipTests

# 单线程（更慢但更稳定）：
mvn compile -q -DskipTests

# 使用特定模块：
mvn -pl patra-registry,patra-ingest compile -q -DskipTests

# 完整的清理编译：
mvn clean compile -q -DskipTests
```

#### 调整错误显示限制

编辑 `.claude/hooks/maven-compile-check.sh`（大约第 47 行）：

```bash
# 默认：显示前 20 个错误
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT" | head -20

# 显示更多错误（例如 50 个）：
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT" | head -50

# 显示所有错误（无限制）：
grep -E "\[ERROR\]|error:|cannot find symbol" "$TEMP_OUTPUT"
```

---

### 技能激活自定义

#### 调整技能触发器模式

编辑 `.claude/skills/skill-rules.json`：

```json
{
  "java-backend-guidelines": {
    "type": "technical",
    "enforcement": "suggest",
    "priority": "high",
    "description": "Java 后端开发指南...",
    "promptTriggers": {
      "keywords": [
        "orchestrator",
        "hexagonal",
        "ddd",
        "spring boot"
      ],
      "intentPatterns": [
        "(how|create|implement).*(controller|service|repository)",
        "(transaction|@Transactional)"
      ]
    },
    "fileTriggers": {
      "pathPatterns": [
        "patra-*/src/main/java/**/*.java"
      ],
      "contentPatterns": [
        "@Service",
        "@RestController",
        "@Repository"
      ]
    }
  }
}
```

**参见**：[../skills/README.md](../skills/README.md) 获取完整的 skill-rules.json 文档。

---

## 环境变量

### 全局环境变量

在 shell 配置文件中设置（`.bashrc`、`.zshrc` 等）：

```bash
# 自定义项目目录（如果不使用默认值）
export CLAUDE_PROJECT_DIR=/path/to/your/project

# Maven 主目录（如果不在 PATH 中）
export M2_HOME=/usr/local/maven
export PATH=$M2_HOME/bin:$PATH
```

### 每个会话的环境变量

在启动 Claude Code 之前设置：

```bash
CLAUDE_PROJECT_DIR=/path/to/project claude-code
```

---

## Hook 执行顺序

Stop hooks 按照在 `settings.json` 中指定的顺序运行：

```json
"Stop": [
  {
    "hooks": [
      { "command": "...maven-compile-check.sh" },          // 首先运行
      { "command": "...trigger-build-resolver-java.sh" }   // 其次运行
    ]
  }
]
```

**为什么这个顺序很重要**：
1. 首先检查编译（检测错误）
2. 然后建议代理（如果找到错误）

---

## 选择性 Hook 启用

你不需要所有的 hooks。选择适合你项目的：

### 最小设置（仅技能激活）

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

### 无 Maven 编译检查

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
    ]
  }
}
```

### 仅 Maven 编译（无技能激活）

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

---

## 缓存管理

### 缓存位置

```
$CLAUDE_PROJECT_DIR/.claude/hooks/.last-compile-failed
```

当 Maven 编译失败时创建此标记文件，显示建议的代理时移除。

### 手动清理

```bash
# 移除编译失败标记
rm -f $CLAUDE_PROJECT_DIR/.claude/hooks/.last-compile-failed
```

---

## 故障排查配置

### Hook 未执行

1. **检查注册**：验证 hook 在 `.claude/settings.json` 中
2. **检查权限**：运行 `chmod +x .claude/hooks/*.sh`
3. **检查路径**：确保 `$CLAUDE_PROJECT_DIR` 设置正确
4. **检查依赖**：运行 `cd .claude/hooks && npm install`

### Maven 编译过慢

**问题**：Hook 运行时间过长

**解决方案**：

1. **禁用多线程**（`maven-compile-check.sh` 第 36 行）：
   ```bash
   # 从以下改为：
   mvn -T 1C compile -q -DskipTests

   # 改为：
   mvn compile -q -DskipTests
   ```

2. **仅编译特定模块**：
   ```bash
   # 在 maven-compile-check.sh 中，改变第 36 行：
   mvn -pl patra-registry,patra-ingest compile -q -DskipTests
   ```

3. **完全跳过此 hook** - 从 `settings.json` 中移除

### 误报检测

**问题**：Hook 对不应该的文件被触发

**解决方案**：在 `maven-compile-check.sh` 中添加跳过条件：

```bash
# 添加到顶部，在设置 PROJECT_ROOT 后
if [[ "$CLAUDE_PROJECT_DIR" =~ /test-fixtures/ ]]; then
    exit 0  # 跳过测试夹具项目
fi
```

### Maven 未找到

**问题**：`mvn: command not found`

**解决方案**：

1. **安装 Maven**：
   ```bash
   # macOS
   brew install maven

   # Linux (Ubuntu/Debian)
   sudo apt-get install maven

   # 验证安装
   mvn --version
   ```

2. **设置 MAVEN_HOME 环境变量**：
   ```bash
   export M2_HOME=/usr/local/maven
   export PATH=$M2_HOME/bin:$PATH
   ```

### 调试 Hooks

向任何 hook 添加调试输出：

```bash
# 在 hook 脚本的顶部
set -x  # 启用调试模式

# 或添加特定的调试行
echo "DEBUG: PROJECT_ROOT=$PROJECT_ROOT" >&2
echo "DEBUG: 运行 mvn compile..." >&2
```

在 Claude Code 的日志中查看 hook 执行。

---

## 高级配置

### 多模块 Maven 项目

对于具有选择性模块编译的项目：

```bash
# 在 maven-compile-check.sh 中，修改第 36 行
# 检测哪些模块已更改并仅编译那些模块

CHANGED_MODULES=$(git diff --name-only HEAD | grep -oP 'patra-\w+' | sort -u | tr '\n' ',' | sed 's/,$//')

if [[ -n "$CHANGED_MODULES" ]]; then
    mvn -pl "$CHANGED_MODULES" compile -q -DskipTests
else
    mvn -T 1C compile -q -DskipTests
fi
```

### 自定义 Maven 配置文件

在编译期间使用特定的 Maven 配置文件：

```bash
# 在 maven-compile-check.sh 中，第 36 行
mvn -T 1C compile -q -DskipTests -P dev

# 或多个配置文件：
mvn -T 1C compile -q -DskipTests -P dev,local
```

### Docker/容器项目

如果 Maven 在容器中运行：

```bash
# 在 maven-compile-check.sh 中，替换第 36 行
docker-compose exec -T app mvn compile -q -DskipTests

# 或使用 Docker run：
docker run --rm -v "$PROJECT_ROOT":/workspace -w /workspace maven:3.9-eclipse-temurin-25 mvn compile -q -DskipTests
```

---

## 最佳实践

1. **从最小开始** - 一次启用一个 hook
2. **彻底测试** - 进行更改并验证 hooks 是否有效
3. **文档化自定义** - 添加注释以解释自定义逻辑
4. **版本控制** - 将 `.claude/` 目录提交到 git
5. **团队一致性** - 在团队中共享配置
6. **性能意识** - 监控 hook 执行时间
7. **优雅降级** - 如果工具丢失，hooks 应该无声地失败

---

## 性能优化

### 更快的编译检查

**选项 1：仅编译更改的模块**

跟踪更改的文件并仅编译受影响的模块：

```bash
# 在 maven-compile-check.sh 中
CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null || echo "")
if [[ -n "$CHANGED_FILES" ]]; then
    # 从更改的文件中提取模块名称
    MODULES=$(echo "$CHANGED_FILES" | grep -oP 'patra-\w+' | sort -u | paste -sd,)

    if [[ -n "$MODULES" ]]; then
        mvn -pl "$MODULES" -am compile -q -DskipTests
        exit $?
    fi
fi

# 回退到完整编译
mvn -T 1C compile -q -DskipTests
```

**选项 2：使用 Maven Daemon**

安装并使用 [mvnd](https://github.com/apache/maven-mvnd) 以加快构建速度：

```bash
# 安装 mvnd
brew install mvnd

# 在 maven-compile-check.sh 中，第 36 行，将 mvn 替换为 mvnd：
mvnd -T 1C compile -q -DskipTests
```

---

## 相关资源

- [README.md](./README.md) - Hooks 概述
- [../skills/README.md](../skills/README.md) - 技能配置
- [../../CLAUDE_INTEGRATION_GUIDE.md](../../CLAUDE_INTEGRATION_GUIDE.md) - 完整的集成指南
