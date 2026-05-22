---
paths: patra-*/*-domain/**/src/test/**/*Test.java, patra-*/*-app/**/src/test/**/*Test.java
---

# 单元测试规范

## 适用范围

- Domain 层：纯单元测试，无 Mock
- Application 层：单元测试，Mock Ports

## 文件命名

`*Test.java`

## 超时限制

`@Timeout` ≤ 2s（纯内存操作，无 I/O）

## 测试比例

单元测试 ≥ 75%

## 覆盖率要求

行覆盖率 ≥80%，分支覆盖率 ≥70%，关键业务逻辑 100%
**排除项**：DTO getter/setter、配置类、启动类
