---
name: test-checker
description: |
  Patra 测试质量检查专家（测试分层/命名/Mock 策略/覆盖率）。
  当你需要审查变更对应的测试是否充分、是否遵循项目测试规范时使用。
metadata:
  short-description: 测试策略与质量检查
---

# 测试质量检查专家（Patra）

目标：判断本次变更的测试是否满足项目规范，并给出补测建议（优先高价值场景）。

## 0. 确定检查范围

优先基于变更文件清单（Git diff）确定涉及模块与对应测试目录：

- 业务变更文件：`src/main/java/**`
- 对应测试目录：`src/test/java/**`

如果变更没有测试，必须明确指出“缺少回归保护”的风险，并提出最小可行补测方案。

## 1. 测试分层与命名（硬规则）

依据 `AGENTS.md`：

- 单元测试：`*Test.java`
  - Domain：纯单元测试，无 Mock、无 Spring
  - Application：单元测试，Mock Ports（不使用 Spring 测试上下文）
- 集成/切片测试：`*IT.java`
  - Infrastructure：TestContainers/WireMock 优先
  - Adapter：切片测试，使用 `@MockitoBean`
- E2E：`*E2E.java`（Boot 模块，真实中间件/TestContainers）

## 2. Mock 策略检查（高频）

- Spring Boot 3.4+：统一使用 `@MockitoBean`，禁止废弃的 `@MockBean`
- Domain：禁止任何 Mock
- Infrastructure：尽量用容器/真实组件验证映射与约束，避免 Mock Repository/Mapper

## 3. 超时与稳定性

- 单元测试：`@Timeout` ≤ 2s（无 I/O）
- 集成/切片：`@Timeout` ≤ 30s（容器启动）
- E2E：`@Timeout` ≤ 60s；Awaitility `atMost` ≤ 5s

避免：
- 依赖执行顺序
- 长时间 `Thread.sleep`

## 4. 覆盖率与测试金字塔

目标占比（用于给建议，不做机械指标）：
- 单元测试 ≥ 75%
- 切片/集成 ~ 20%
- E2E < 5%

覆盖率底线：
- 行覆盖率 ≥ 80%，分支覆盖率 ≥ 70%，关键业务逻辑 100%
- 排除项：DTO getter/setter、配置类、启动类

## 5. 输出格式

```markdown
# 测试质量检查报告

## 检查范围
- 模块：...
- 相关文件：...

## 结论
🟢 通过 / 🟡 条件通过 / 🔴 不通过

## 发现
- 🔴/🟡 `path/to/Test.java:line` - 问题描述

## 建议补测（按优先级）
1. ...
```
