---
type: adr
adr_id: 2
date: 2025-11-26
status: accepted
date_decided: 2025-11-26
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/testing
  - tech/testcontainers
---

# ADR-002: Infra 层采用集成测试替代单元测试

## 状态

**accepted**

## 背景

Infra 层 Repository 实现的单元测试价值有限——Mock Mapper 只能验证方法调用，无法验证 SQL 语句、类型映射、MyBatis-Plus 拦截器等实际行为。测试通过不代表生产环境正常工作。

## 决策

我们将对 Infra 层 Repository 采用 TestContainers + MySQL 集成测试，废弃 Mock-based 单元测试。

## 后果

### 正面影响

- **真实验证**：测试实际 SQL 执行、字段映射、JSON 序列化
- **发现隐藏问题**：如 `BIGINT(20)` 废弃警告、TypeHandler 配置错误
- **重构安全**：修改 Mapper XML 时有真实反馈
- **测试代码更简洁**：无需复杂 Mock 设置

### 负面影响

- **首次运行开销**：首次运行需拉取 Docker 镜像（后续 JVM 级复用）
- **测试耗时增加**：单次测试耗时约 15s（可接受，换取真实验证）

### 风险

- 开发环境需要安装 Docker
- CI 环境需配置 Docker-in-Docker 或 privileged 模式

## 替代方案

### 方案 A：继续使用 Mock-based 单元测试

使用 Mockito Mock Mapper 接口，验证方法调用。

**优点**：
- 运行速度快，无外部依赖
- 无需 Docker 环境

**缺点**：
- 无法验证 SQL 正确性
- 无法发现 TypeHandler、拦截器配置问题
- 测试通过不代表生产可用

### 方案 B：使用 H2 内存数据库

使用 H2 替代 MySQL 进行测试。

**优点**：
- 运行速度快
- 无需 Docker

**缺点**：
- H2 与 MySQL 语法不完全兼容
- 无法验证 MySQL 特有功能（如 JSON 字段、空间索引）
- 可能出现「测试通过，生产失败」的情况

## 参考资料

- [TestContainers MySQL Module](https://java.testcontainers.org/modules/databases/mysql/)
- [Spring Boot Testing with TestContainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
