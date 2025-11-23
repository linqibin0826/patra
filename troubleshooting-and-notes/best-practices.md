# 最佳实践

1. 所有方法（任何访问级别）必须编写 JavaDoc 注释
2. 禁止在 Mapper 接口中使用 `@Select` 等注解编写 SQL，简单查询使用 `LambdaQueryWrapper`，复杂查询使用 XML
3. `patra-{service}-api` 模块仅定义服务间调用的接口和 DTO（不包含 Controller）
4. `patra-{service}-adapter` 模块必须依赖 `patra-spring-boot-starter-web`，禁止在 adapter 层重复实现已有功能（全局异常处理、参数验证、类型转换、统一响应模型等由 Starter 统一提供）
5. 任何使用 Feign 客户端的模块必须依赖 `patra-spring-cloud-starter-feign`，禁止手动配置 `@EnableFeignClients` 或自定义错误解码器（Feign 客户端自动扫描、错误解码、追踪传播、服务标识传播等由 Starter 统一提供）
6. `patra-{service}-infra` 模块涉及数据库操作时必须依赖 `patra-spring-boot-starter-mybatis`，所有 DO 必须继承 `BaseDO`，禁止重复实现已有功能（MyBatis-Plus 插件、审计字段自动填充、JSON 类型处理器、Mapper 自动扫描、Flyway 迁移等由 Starter 统一提供）
7. `patra-{service}-infra` 模块涉及对象存储（MinIO/S3）时必须依赖 `patra-spring-boot-starter-object-storage`，使用统一的 `ObjectStorageOperations` 接口，禁止直接使用 MinIO/S3 SDK（统一存储抽象、自动重试、指标收集、存储位置解析、文件大小限制、Bucket 自动管理等由 Starter 统一提供）
8. 所有非 domain 模块（adapter、app、infra、api）必须依赖 `patra-spring-boot-starter-core`，domain 模块禁止依赖（JSON/XML 序列化标准化配置、错误处理框架与追踪传播、可观测性集成、统一 UTC 时间源、熔断保护等基础设施由 Starter 统一提供）
9. 所有服务的所有层（domain、app、infra、adapter、api）必须依赖 `patra-common-core`（提供 DDD 领域基类 AggregateRoot/DomainEvent、异常体系 DomainException/ApplicationException/ErrorTrait、共享枚举 ProvenanceCode/Priority、JSON 标准化 JsonNormalizer、通用工具 HashUtils 等核心基础设施）
10. 需要标准化对象存储键生成时依赖 `patra-common-storage`，使用 `ObjectKeyTemplate.generateDailyKey()` 生成日期分区键（格式：`{service}/{business-type}/{yyyy/MM/dd}/{business-id}.{ext}`），禁止手动拼接存储路径
11. 需要跨服务共享数据模型时依赖 `patra-common-model`（提供 Shared Kernel 契约，如 CanonicalPublication、PlanMetadata 继承体系等），禁止在各服务中重复定义相同概念
12. Domain 层端口接口命名：**Repository** 后缀用于持久化聚合根/实体（本地数据库），**Port** 后缀用于外部服务和技术基础设施（外部 API/消息队列/对象存储等），禁止混用
13. `patra-{service}-boot` 模块是应用启动入口，必须包含 `@SpringBootApplication` 启动类（命名规范：`Patra{Service}Application`），负责组装所有依赖（adapter、app、infra），禁止在其他模块添加启动类
14. `patra-{service}-infra` 模块涉及 REST API 调用或文件下载时必须依赖 `patra-spring-boot-starter-rest-client`，使用统一的 `defaultRestClient` Bean，禁止直接依赖 `spring-web` 或手动创建 RestClient（统一超时配置、请求日志、追踪传播、重试机制、指标收集等由 Starter 统一提供）
15. 测试遵循金字塔原则：**单元测试** 75%+（domain/app 层纯 JUnit，无 Spring 容器）→ **切片测试** 20%（infra/adapter 层 `@MybatisPlusTest`/`@WebMvcTest` 加载必要组件）→ **E2E 测试** <5%（boot 层 `@SpringBootTest` 启动完整应用验证关键流程）
16. 禁止在代码中使用全类名（Fully Qualified Name），必须使用 `import` 语句导入类型（特例：仅当类名冲突时使用全类名消歧义）
17. 优先使用 Lombok 注解（`@Getter`、`@Setter`、`@Data`、`@Builder`、`@AllArgsConstructor`、`@NoArgsConstructor` 等）生成 getter/setter/constructor/toString/equals/hashCode，仅在需要自定义逻辑时才手动编写
18.