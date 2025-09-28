架构与分层：六边形架构 + DDD，严格依赖方向，不越层不泄漏实现。
POJO 形态：值对象优先使用 record；可变对象使用 Lombok（class 上合理选用 @Data/@Getter/@Setter 等），record 内不使用 Lombok。
JSON 字段：数据库 JSON 字段在 DO 中统一使用 Jackson JsonNode。
工具复用：优先 Hutool 与 patra-common/starters，避免重复造轮子。
事务与一致性：应用层编排事务；跨聚合通过事件实现最终一致；domain 层不引入事务框架。
测试：JUnit5 + Spring Boot Test + MyBatis-Plus Test + AssertJ + Mockito；单测在各子模块，集成测试在 {service}-boot；测试数据用 H2 或 Testcontainers。
日志：@Slf4j，ERROR 系统异常；WARN 业务违例；INFO 关键操作；DEBUG 诊断；参数化日志与异常堆栈打印；贯穿 trace/correlation ID。
Flyway：各微服务独立管理；路径 patra-{service}-infra/src/main/resources/db/migration；命名 V{version}__{description}.sql。
安全配置：密钥/连接串/可变配置走 Nacos/环境变量；不在代码中硬编码。