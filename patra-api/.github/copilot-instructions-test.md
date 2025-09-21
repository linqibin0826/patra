# Copilot Instructions for `medoc-api`

> **请严格遵循本文档；若有冲突，以本文档为准。若生成的字符或文件有乱码，不需要处理，我会自己写修复**

---

## 1. 项目背景

- **目标**：构建医学文献数据平台的**数据底座（Step-1）**，完成**跨源采集 → 元数据编目**的闭环，后续支持上层搜索、推荐与分析。

---

## 2. 技术栈

- **语言/运行时**：Java **25**
- **框架**：Spring Boot 3.2.x、MyBatis-Plus（MP）、Lombok、MapStruct。
- **数据库迁移**：Flyway（脚本位于 `src/main/resources/db/migration`）。
- **测试**：JUnit 5、Mockito、AssertJ。

---

## 3. 架构形态

- SpringCloud微服务。
- 系统使用六边形架构，按**端口/适配器（Hexagonal）**约束边界。
- 领域通过 **接口（port）** 协作，不跨层透传实现细节。

---

## 4. 分层与目录约束

- **infra模块中的实体DO**：所有数据库实体**必须继承** `BaseDO`（含 `id/version/createdAt/updatedAt/createdBy/.../deleted`
  等审计字段）。名字统一以Entity结尾，去掉模块前缀。
- **Repository 实现层**：
    - **简单查询**：优先使用 **BaseMapper** 通用 API。
    - **复杂查询**（多表 JOIN/窗口/统计）：在 `src/main/resources/mapper/**` 添加 `mapper.xml`。
    - 在xml中生成sql语句时，无论是否 join ，表都必须起别名！

---

## 5. 设计取舍

- **不过度设计**：出现 ≥2 次重复或可预见扩展时再抽象接口/策略/工厂。

---

## 6. 高内聚、低耦合与可测性

- 模块内职责单一；模块间通过接口协作，DI 解耦实现。
- 方法短小、聚焦单一任务；避免巨型方法。
- 面向接口编程（如 `List<String> list = new ArrayList<>()`）；在**合适**场景使用工厂/策略/观察者/单例等模式解决**重复**问题。
- 遵循 **SOLID**、**DRY**、**KISS**；**可读性优先**，避免晦涩技巧。
- 日志使用 SLF4J；**禁止** `System.out.println`；日志包含关键上下文（但**不泄露敏感信息**）。

---

## 8. 文档与 Javadoc

- **所有类**必须有 Javadoc，含 `@author`、`@since 0.1.0`、必要时 `@see/@link`。
- 为**公共类/方法**及**非简单私有方法**编写清晰 Javadoc，解释 **What** 与 **Why**（非 How）。
- 使用 `@param`、`@return`、`@throws` 详尽记录方法签名。

---

## 9. Lombok 使用规范

- **优先**：`@Getter`、`@Setter`、`@ToString`、`@EqualsAndHashCode`；**少用** `@Data`。
- 复杂对象用 `@SuperBuilder`，并对必填字段做校验。
- 仅在需要时使用 `@AllArgsConstructor`、`@NoArgsConstructor`；注意与 ORM 反射需求兼容。
- 避免在 `@ToString` 中输出敏感字段，可用 `@ToString.Exclude`。

