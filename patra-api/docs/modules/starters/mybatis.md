# 模块：patra-spring-boot-starter-mybatis

统一提供 MyBatis-Plus 在 Papertrace 体系中的“最小可用 + 规范约束”能力：

1. 插件链：分页 / 乐观锁 / 防全表更新删除
2. 元数据自动填充（时间戳等）
3. 数据层异常到标准 HTTP 错误码映射（接入核心错误解析管线）
4. 统一 DO 基类 `BaseDO`（审计字段 / 逻辑删除 / 乐观锁）
5. JSON 字段无侵入：`JsonNode` / `Map<String,Object>` TypeHandler
6. 约定 Mapper 扫描路径（infra 层）

---

## 1. 快速开始

```xml
<dependency>
	<groupId>com.papertrace</groupId>
	<artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

应用模块 `infra` 层新增 Mapper：
```java
@Mapper
public interface ArticleMapper extends BaseMapper<ArticleEntity> { }
```

---

## 2. 自动配置组件

| 类 | 作用 | 条件 |
|----|------|------|
| `PatraMybatisAutoConfiguration` | 注册数据层错误映射贡献者 | 存在 `MapperScannerConfigurer` |
| `MybatisPluginAutoConfig` | 分页/乐观锁/防全表插件 + 元数据填充器 | 总是 |
| `DataLayerErrorMappingContributor` | DB/驱动异常 → 标准 HTTP 语义错误码 | 注入核心错误解析 |

> 不负责 DataSource / 事务管理；这些在业务模块中配置。

---

## 3. 插件链说明

顺序（MyBatis-Plus 内部按添加先后）：
1. `PaginationInnerInterceptor`：方言 MySQL，防止溢出与自动分页封装
2. `OptimisticLockerInnerInterceptor`：基于 `@Version` 字段的 CAS 保护
3. `BlockAttackInnerInterceptor`：阻断无 where 条件的 UPDATE / DELETE

---

## 4. 元数据自动填充策略

`MetaObjectHandler` 逻辑：
| 场景 | 字段 | 值来源 |
|------|------|--------|
| INSERT | createdAt / updatedAt | `Instant.now(clock?)` |
| UPDATE | updatedAt | `Instant.now(clock?)` |

> createdBy / updatedBy 等字段当前留空（留待权限体系集成）。

---

## 5. 数据层异常映射 (`DataLayerErrorMappingContributor`)

| 异常类别 | 规则 | 映射 HTTP 语义 |
|----------|------|----------------|
| `MybatisPlusException` | 统一 | 500 INTERNAL_ERROR |
| `SQLIntegrityConstraintViolationException` | 重复键 / 约束 | 409 CONFLICT |
| `SQLException` errorCode=1062/1452/1451 | 常见唯一/外键 | 409 CONFLICT |
| `SQLException` sqlState 08* / HY* | 连接/超时 | 503 UNAVAILABLE |
| 其它 `SQLException` | 默认 | 500 INTERNAL_ERROR |

> 贡献者返回标准 `HttpStdErrors.Group` 中的错误码，后续由核心管线统一包装 ProblemDetail。

---

## 6. 基类 `BaseDO`

| 字段 | 说明 |
|------|------|
| id | ASSIGN_ID（雪花/MP 内部策略） |
| recordRemarks | JSON 文本（建议与 JsonNode TypeHandler 配合） |
| createdAt / updatedAt | 时间戳（自动填充） |
| createdBy / updatedBy (+Name) | 预留审计人字段 |
| version | 乐观锁版本 |
| ipAddress | 原始请求 IP 二进制（IPv4/IPv6） |
| deleted | 逻辑删除标识（0/1） |

---

## 7. JSON TypeHandler

| 处理器 | Java ↔ DB | 特性 |
|--------|-----------|------|
| `JsonToJsonNodeTypeHandler` | `JsonNode` ↔ JSON/TEXT/CLOB/jsonb | 多形态解析 / 宽容空白 / PGobject 支持 |
| `JsonToMapTypeHandler` | `Map<String,Object>` ↔ JSON/TEXT | 轻量校验：首字符必须 `{` |

注册方式：通过 `@MappedTypes` + Spring 注入 `ObjectMapper` 自动生效（放入扫描包）。

---

## 8. Mapper 扫描约定

默认建议路径：`com.patra.**.infra.persistence.mapper`（可在业务模块用 `@MapperScan` 自定义/追加）。

---

## 9. 扩展点

| 场景 | 扩展策略 |
|------|----------|
| 异常映射增强 | 新增 `ErrorMappingContributor` Bean（将与现有 contributor 聚合） |
| 字段自动填充 | 自定义实现 `MetaObjectHandler`（标记 `@Primary` 覆盖） |
| JSON 类型 | 自定义 TypeHandler + 配置 `mybatis-plus.type-handlers-package` |
| Mapper 路径 | 使用 `@MapperScan` / MP 配置项 |

---

## 10. 最佳实践

| 目的 | 建议 |
|------|------|
| 避免大结果集内存爆炸 | 分页必传 limit / 使用分页插件 Page | 
| 乐观锁冲突 | 捕获 MP `OptimisticLockingFailure` 并转业务重试/提示 |
| 重复键/外键提示 | 通过 ProblemDetail.code 在前端映射人性化文案 |
| JSON 字段演进 | 避免 schema 强依赖，后端使用 `JsonNode` 按需取值 |

---

## 11. Roadmap

| 优先级 | 项目 | 描述 |
|--------|------|------|
| High | 多租户插件封装 | 统一租户字段注入与隔离策略 |
| High | 审计填充集成 | 接入用户上下文填充 createdBy 等 |
| Mid | SQL 慢日志与分类指标 | 与核心观测体系对接 |
| Mid | JSON Schema 校验 | 入库前可选启用结构验证 |
| Low | 软删除恢复工具 | 批量恢复 & 物理清理辅助脚本 |

---

## 12. FAQ

| 问题 | 回答 |
|------|------|
| 事务在哪里配置？ | 在业务模块（boot/infra config）使用 Spring Transaction + DataSource |
| 错误码如何生成？ | 数据层 contributor 只做语义映射，最终错误码由核心错误上下文 + context-prefix 生成 |
| JSON 字段为什么用 JsonNode? | 免 DTO/DO 频繁演进 & 支持局部读取/透传 |
| 乐观锁失败如何处理? | 返回 CONFLICT 语义，前端可提示“已被修改，请刷新后再试” |

---

## 13. 参考源码

| 位置 | 说明 |
|------|------|
| `autoconfig/MybatisPluginAutoConfig.java` | 插件链 + MetaObjectHandler |
| `autoconfig/PatraMybatisAutoConfiguration.java` | 数据层错误映射注册 |
| `error/contributor/DataLayerErrorMappingContributor.java` | 异常分类规则 |
| `entity/BaseDO/BaseDO.java` | DO 基类定义 |
| `type/JsonToJsonNodeTypeHandler.java` | JsonNode 处理器 |
| `type/JsonToMapTypeHandler.java` | Map JSON 处理器 |

---

如需新增能力请附：异常样例 / 期望映射语义 / 影响面。欢迎 PR。

