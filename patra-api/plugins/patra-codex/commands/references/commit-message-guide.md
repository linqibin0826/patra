# 提交消息与分组指南

## 目录

- 提交分组规则
- Conventional Commits 格式
- 常用 type
- 推荐 scope
- 中文消息约束
- 提交计划模板

## 提交分组规则

- 同一功能链路的文件应归为一个提交，例如 Controller + Handler + RepositoryAdapter + 测试。
- 同一缺陷修复涉及的实现与测试应优先放在同一提交。
- 文档与代码若逻辑独立，应分开提交。
- 大范围重构应优先按“一个可审查逻辑单元”拆分，而不是机械按目录拆。

## Conventional Commits 格式

```text
<type>(<scope>): <简短描述>

<详细说明，可选>
```

## 常用 type

- `feat`
- `fix`
- `refactor`
- `docs`
- `test`
- `style`
- `chore`
- `perf`
- `ci`
- `build`

## 推荐 scope

- 微服务：`ingest`、`registry`、`catalog`、`gateway`
- 分层：`domain`、`app`、`infra`、`adapter`、`api`
- Starter：`starter-web`、`starter-jpa`、`starter-rest-client`
- 公共模块：`common-core`、`common-model`

## 中文消息约束

- 第一行使用中文，50 字以内。
- 用动词开头，例如“添加”“修复”“重构”“更新”“移除”。
- 正文解释“为什么这样改”，不要只重复“改了什么”。
- 破坏性变更用 `BREAKING CHANGE:` 标识。

## 提交计划模板

```text
提交 1/2
类型: feat(catalog)
描述: 添加期刊导入命令处理流程
文件:
- path/to/file1
- path/to/file2
```
