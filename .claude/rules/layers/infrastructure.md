---
paths: patra-*/*-infra/**/*.java
---

# Infrastructure 层开发规范

## 核心职责

- 实现端口接口（Repository/Port）
- 技术适配：数据库、消息队列、外部 API
- 数据转换：JpaEntity ↔ Domain Entity

## 被动适配器命名

- `{Entity}Repository` → `{Entity}RepositoryAdapter`（放 `adapter/persistence/`）
- `{Function}Port` → `{Function}Adapter`（放 `adapter/{function}/`）

## 对象转换

- 使用 MapStruct 进行 DO/DTO/Entity 转换，禁止手动编写

## 异常处理

- 通过 `ErrorMappingContributor` SPI 映射第三方异常

## Starter 依赖

- 数据库：`starter-jpa`
- 对象存储：`starter-object-storage`
- REST 调用：`starter-rest-client`
- Feign 调用：`cloud-starter-feign`
