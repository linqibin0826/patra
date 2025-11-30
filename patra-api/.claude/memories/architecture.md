# 架构规范

## 六边形架构层级

- **Domain**：纯业务逻辑，禁止依赖任何框架
- **Application**：编排领域服务，管理事务边界（`@Transactional`）
- **Infrastructure**：实现 Domain 定义的端口接口（Repository/Port）
- **Adapter**：入站适配（Controller/Job/Listener），调用 Application 层

## 模块职责

- `api` 模块：仅定义服务间接口和 DTO，禁止含 Controller
- `boot` 模块：唯一启动入口（`Patra{Service}Application`），组装所有依赖
- 端口命名：`Repository` 用于本地持久化，`Port` 用于外部服务/技术基础设施

## 被动适配器命名

Infrastructure 实现统一用 `*Adapter` 后缀：
- `{Entity}Repository` → `{Entity}RepositoryAdapter`（放 `adapter/persistence/`）
- `{Function}Port` → `{Function}Adapter`（放 `adapter/{function}/`）
- `{Service}Client` → `{Service}ClientAdapter`（保持原位置）

## Starter 依赖

- adapter 层 → `starter-web`（全局异常/验证/响应）
- Feign 调用 → `cloud-starter-feign`
- 数据库 → `starter-mybatis`（DO 继承 `BaseDO`）
- 对象存储 → `starter-object-storage`（用 `ObjectStorageOperations`）
- REST 调用 → `starter-rest-client`（用 `defaultRestClient` Bean）
- 非 domain 模块 → `starter-core`；所有层 → `common-core`
- Spring 模块测试 → `starter-test`（禁止重复声明测试依赖）

## Common 模块

- `common-storage`：`ObjectKeyTemplate.generateDailyKey()` 生成存储键
- `common-model`：Shared Kernel 跨服务共享模型

## Adapter 开发模式

Application 层：`{Feature}UseCase`（接口）+ `{Feature}Orchestrator`（实现）+ `{Feature}Command`（含验证）+ `{Feature}Result`

Adapter 层：Controller/Job/Listener 只做协议转换，依赖 UseCase 接口，禁止业务逻辑。各层参数传递必须用 POJO。
