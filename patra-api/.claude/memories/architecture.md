# 架构与模块依赖规范

## 六边形架构依赖规则

1. Domain 层不依赖任何层，保持纯业务逻辑，禁止依赖任何框架
2. Application 层只依赖 Domain 层，负责编排领域服务和管理事务边界
3. Infrastructure 层实现 Domain 层定义的端口接口（Repository、外部服务调用、消息发布）
4. Adapter 层调用 Application 层，负责 HTTP 接口、消息监听、定时任务等入站适配

## 模块职责与边界

1. `patra-{service}-api` 模块仅定义服务间调用的接口和 DTO，禁止包含 Controller
2. `patra-{service}-boot` 模块是应用启动入口，必须包含 `@SpringBootApplication` 启动类（命名规范：`Patra{Service}Application`），负责组装所有依赖（adapter、app、infra），禁止在其他模块添加启动类
3. Domain 层端口接口命名：`Repository` 后缀用于持久化聚合根/实体（本地数据库），`Port` 后缀用于外部服务和技术基础设施（外部 API/消息队列/对象存储等），禁止混用

## Starter 依赖规范

1. `patra-{service}-adapter` 模块必须依赖 `patra-spring-boot-starter-web`，禁止重复实现全局异常处理、参数验证、类型转换、统一响应模型等功能
2. 使用 Feign 客户端的模块必须依赖 `patra-spring-cloud-starter-feign`，禁止手动配置 `@EnableFeignClients` 或自定义错误解码器
3. `patra-{service}-infra` 模块涉及数据库操作时必须依赖 `patra-spring-boot-starter-mybatis`，所有 DO 必须继承 `BaseDO`，禁止重复实现 MyBatis-Plus 插件、审计字段填充等功能
4. `patra-{service}-infra` 模块涉及对象存储时必须依赖 `patra-spring-boot-starter-object-storage`，使用 `ObjectStorageOperations` 接口，禁止直接使用 MinIO/S3 SDK
5. `patra-{service}-infra` 模块涉及 REST API 调用时必须依赖 `patra-spring-boot-starter-rest-client`，使用 `defaultRestClient` Bean，禁止手动创建 RestClient
6. 所有非 domain 模块（adapter、app、infra、api）必须依赖 `patra-spring-boot-starter-core`，domain 模块禁止依赖
7. 所有服务的所有层必须依赖 `patra-common-core`（提供 DDD 基类、异常体系、共享枚举、工具类等）
8. 所有 Spring 模块（`patra-{service}-*` 除 domain 和 api 层、`patra-spring-boot-starter-*`、`patra-spring-cloud-starter-*`）测试时必须依赖 `patra-spring-boot-starter-test`（提供 TestContainers 容器初始化器、ArchUnit 架构规则、测试工具集），禁止重复声明 JUnit、AssertJ、Mockito、TestContainers、ArchUnit、Awaitility 等已传递的依赖；`patra-common-*`、`patra-{service}-api` 等纯 Java 模块不适用此规则

## Common 模块使用规范

1. 需要标准化对象存储键生成时依赖 `patra-common-storage`，使用 `ObjectKeyTemplate.generateDailyKey()` 生成日期分区键，禁止手动拼接存储路径
2. 需要跨服务共享数据模型时依赖 `patra-common-model`（Shared Kernel 契约），禁止在各服务中重复定义相同概念

## Adapter 层入口开发规范

Adapter 层负责入站适配（Controller、定时任务、消息监听），统一遵循以下模式。

### 包结构

```
patra-{service}-app/usecase/{feature}/
├── {Feature}UseCase.java           # 接口（Adapter 依赖此接口）
├── {Feature}Orchestrator.java      # 实现（implements UseCase）
├── command/{Feature}Command.java   # 命令（含参数验证）
└── dto/{Feature}Result.java        # 结果

patra-{service}-adapter/
├── rest/{Feature}Controller.java   # HTTP 入口
├── scheduler/job/{Feature}Job.java # 定时任务入口
└── mq/{Feature}Listener.java       # 消息监听入口
```

### 开发流程

1. **Application 层**：创建 `{Feature}UseCase` 接口定义用例契约，`{Feature}Orchestrator` 实现该接口
2. **Command/Result**：`{Feature}Command` 在构造函数中完成参数验证，`{Feature}Result` 封装执行结果
3. **Adapter 层**：入口类只做协议转换（DTO/JSON/Message → Command），禁止包含业务逻辑
4. **依赖方向**：Adapter 依赖 UseCase 接口（非 Orchestrator 实现），实现依赖倒置
5. **参数传递**：各层之间传递参数必须使用 POJO（Command/Result/DTO），禁止使用多个简单类型参数，面向对象开发

### 职责划分

| 层级 | 组件 | 职责 | 禁止行为 |
|------|------|------|----------|
| Adapter | Controller/Job/Listener | 协议转换、日志、响应封装 | 业务逻辑、复杂验证 |
| Adapter | DTO/Param | 请求反序列化 | 业务验证 |
| Application | Command | 参数验证、枚举转换 | 业务逻辑 |
| Application | UseCase | 定义用例契约 | 实现细节 |
| Application | Orchestrator | 业务编排、事务管理 | 直接依赖框架 |
