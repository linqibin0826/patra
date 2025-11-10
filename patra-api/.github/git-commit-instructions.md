# Git Commit 规范

本项目遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范，以确保提交历史清晰、可追溯。

## 提交消息格式

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### 必填部分

- **type**：提交类型（必填，使用英文）
- **subject**：简短描述（必填，**使用中文**）

### 可选部分

- **scope**：影响范围（推荐，使用英文）
- **body**：详细描述（可选，使用中文）
- **footer**：备注信息（可选，使用中文）

---

## Type（提交类型）

**注意：type 必须使用英文关键字**

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(ingest): 添加 PubMed 批量计划编排器` |
| `fix` | 修复 Bug | `fix(registry): 修复 Provenance 查找空指针异常` |
| `docs` | 文档变更 | `docs: 更新架构设计指南` |
| `style` | 代码格式（不影响功能） | `style(domain): 格式化 BatchPlan 类代码` |
| `refactor` | 重构（不改变功能） | `refactor(app): 提取校验逻辑到领域层` |
| `test` | 测试相关 | `test(domain): 为 BatchPlan 聚合根添加单元测试` |
| `chore` | 构建/工具/依赖 | `chore: 升级 Spring Boot 到 3.5.7` |
| `perf` | 性能优化 | `perf(test): 优化 GenericBatchExecutor 重试测试延迟` |
| `ci` | CI/CD 配置 | `ci: 优化 CI 流程，减少无代码变更时的构建` |
| `build` | 构建系统变更 | `build: 更新 Maven wrapper 到 3.9.9` |
| `revert` | 回滚提交 | `revert: 回滚"feat(ingest): 添加批量计划功能"` |

---

## Scope（影响范围）

Scope 是可选的，但**强烈推荐**添加，用于标识提交影响的模块或组件。

**注意：scope 使用英文小写**

### 常用 Scope

| Scope | 说明 |
|-------|------|
| `ingest` | patra-ingest 微服务 |
| `registry` | patra-registry 微服务 |
| `gateway` | patra-gateway 微服务 |
| `common` | patra-common 公共模块 |
| `domain` | 领域层（domain 包） |
| `app` | 应用层（app 包） |
| `infra` | 基础设施层（infra 包） |
| `adapter` | 适配器层（adapter 包） |
| `api` | API 定义模块 |
| `test` | 测试相关 |
| `ci` | CI/CD 配置 |
| `config` | 配置相关 |
| `docs` | 文档 |

### Scope 格式

- 使用小写字母、数字、连字符（`-`）和下划线（`_`）
- 避免使用空格
- 尽量简洁明了

**示例：**
```
feat(ingest): 添加批量计划编排器
fix(registry-api): 修复 Provenance DTO 校验逻辑
test(domain-model): 添加 BatchPlan 单元测试
```

---

## Subject（提交描述）

**使用中文描述**

### 规则

1. **长度限制**
   - 推荐：≤ 25 个汉字（50 字符）
   - 最大：≤ 36 个汉字（72 字符）

2. **语气要求**
   - ✅ 使用**动宾结构**（动词 + 宾语）
   - ✅ 正确：`添加功能`、`修复问题`、`更新文档`
   - ❌ 错误：`已添加功能`、`已修复问题`、`已更新文档`

3. **标点符号**
   - 不使用句号结尾
   - 可使用连字符（-）分隔多个要点

4. **描述准确性**
   - 清晰说明做了什么
   - 避免模糊词汇（如"优化代码"、"修改逻辑"）
   - 具体说明改动内容

### 示例对比

| ❌ 错误 | ✅ 正确 |
|---------|---------|
| `已添加新功能` | `添加新功能` |
| `修复了 Bug。` | `修复 Bug` |
| `更新一下文档` | `更新文档` |
| `feat: 添加了 PubMed 解析器` | `feat: 添加 PubMed 解析器` |
| `fix: 优化代码` | `fix: 修复批量处理空指针异常` |

---

## Body（详细描述）

Body 是可选的，用于提供更详细的变更说明。**使用中文**。

### 何时需要 Body

- 复杂的功能变更
- 需要解释**为什么**做这个改动
- 需要说明**如何**解决问题
- 有破坏性变更（Breaking Changes）

### 格式要求

- 与 subject 之间**空一行**
- 每行不超过 72 字符（约 36 个汉字）
- 可以分多段
- 使用动宾结构

### 示例

```
feat(ingest): 添加批量计划编排器

实现新的编排器来规划 PubMed 文章的批量处理。
这替换了旧的单体处理逻辑，采用更模块化的方法。

- 提取批量计划逻辑到独立服务
- 支持动态批量大小配置
- 改进错误处理和重试机制
```

---

## Footer（页脚信息）

Footer 用于记录特殊信息，如关闭 Issue、破坏性变更等。**使用中文**。

### Breaking Changes（破坏性变更）

如果提交包含破坏性变更，必须在 Footer 中标明：

```
feat(api)!: 变更 Provenance API 响应格式

破坏性变更：Provenance API 现在返回嵌套结构而不是扁平结构。
客户端需要更新集成代码。

变更前：{ "id": 1, "name": "PubMed" }
变更后：{ "provenance": { "id": 1, "name": "PubMed" } }
```

### 关联 Issue

```
fix(ingest): 修复批量大小计算错误

修复 #123
关闭 #456
相关 #789
```

---

## 完整示例

### 示例 1：简单功能

```
feat(ingest): 添加 PubMed 批量计划编排器
```

### 示例 2：带详细描述

```
fix(registry): 修复 Provenance 查找空指针异常

当缓存为空时，Provenance 查找会失败，
导致下游服务出现 NullPointerException。

- 访问缓存前添加空值检查
- 返回空 Optional 而不是 null
- 为边界情况添加单元测试

修复 #234
```

### 示例 3：破坏性变更

```
feat(api)!: 迁移到新的认证机制

破坏性变更：API 认证现在需要 JWT token。
不再支持 Basic Auth。

迁移指南：
1. 通过 /auth/token 端点生成 JWT token
2. 在 Authorization header 中包含 token
3. 更新所有 API 客户端

关闭 #567
```

### 示例 4：重构

```
refactor(app): 提取校验逻辑到领域层

将校验逻辑从应用层移到领域层，
更好地符合 DDD 原则并提高可测试性。

- 在领域层创建 Validator 值对象
- 更新编排器使用领域校验器
- 添加完整的单元测试
```

### 示例 5：性能优化

```
perf(test): 优化 GenericBatchExecutor 重试测试延迟，加快单元测试执行
```

### 示例 6：测试修复

```
fix(test): 修复集成测试 TestContainers 端口冲突 - 使用单进程执行
```

---

## 验证机制

项目配置了 Git Hook 自动验证提交消息格式：

### 自动验证

- 提交时会自动检查格式
- 不符合规范会被拒绝
- 仅验证格式，不验证语言（中英文均可）

### 跳过验证（不推荐）

```bash
# 临时跳过（不推荐）
git commit --no-verify -m "your message"

# 或设置环境变量
SKIP_COMMIT_MSG=1 git commit -m "your message"
```

### 验证脚本

参见：`scripts/git/validate_commit_msg.sh`

---

## 常见问题

### Q1: Scope 必须填吗？

**建议填写**，但不是强制的。添加 scope 可以让提交历史更清晰。

### Q2: 必须用中文吗？

**是的**，项目规范要求 subject 和 body 使用中文，但 type 和 scope 必须使用英文关键字。

### Q3: 如何修改已提交的消息？

```bash
# 修改最后一次提交
git commit --amend

# 修改历史提交（谨慎使用）
git rebase -i HEAD~N
```

### Q4: Merge commit 需要遵循规范吗？

不需要，合并提交会自动跳过验证。

### Q5: 描述太长怎么办？

- 如果超过 25 个汉字，考虑拆分或简化
- 将详细信息移到 body 部分
- 使用连字符（-）分隔多个要点

---

## 参考资源

- [Conventional Commits 规范](https://www.conventionalcommits.org/)
- [Angular Commit Message Guidelines](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#commit)
- 项目验证脚本：`scripts/git/validate_commit_msg.sh`

---

## 快速参考

```bash
# 功能开发
git commit -m "feat(ingest): 添加 PubMed 解析器"

# Bug 修复
git commit -m "fix(registry): 修复 Provenance 查找空指针异常"

# 文档更新
git commit -m "docs: 更新 README 部署指南"

# 重构
git commit -m "refactor(domain): 提取校验逻辑到值对象"

# 测试
git commit -m "test(app): 为编排器添加单元测试"

# 性能优化
git commit -m "perf(infra): 优化数据库查询性能"

# CI/CD
git commit -m "ci: 添加测试覆盖率报告"

# 配置变更
git commit -m "fix(config): 更新测试配置应用名称并升级 ArchUnit 版本"

# 复杂描述（使用连字符）
git commit -m "fix(test): 修复集成测试 TestContainers 端口冲突 - 使用单进程执行"
```

---

**遵循规范，让提交历史更清晰！**
