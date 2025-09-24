# Copilot Instructions · Papertrace

本仓库采用 **六边形架构（Hexagonal / Ports & Adapters） + DDD 分层**的医学文献数据平台。  
AI 编程助手在生成或改写代码时必须严格遵守以下约束。

---

## 1. 架构概览

### 大型项目结构
- **Papertrace**：医学文献数据平台，目标是统一采集10+医学文献源（PubMed、EPMC）
- **多模块微服务**：Gateway + Registry(SSOT) + Ingest(采集) + 表达式引擎 + 自研Starters
- **技术栈**：Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.1, MyBatis-Plus 3.5.12

### 核心服务边界
```
patra-registry/     # SSOT - 配置/字典/元数据管理
patra-ingest/       # 采集引擎 - 文献数据抓取解析
patra-gateway-boot/ # API网关
patra-expr-kernel/  # 表达式引擎核心
patra-spring-boot-starter-*/ # 错误处理/Web/MyBatis/表达式 starters
```

---

## 2. 分层架构与依赖方向

### 严格依赖约束
- **adapter** → **app** + **api** (可选 web starters)
- **app** → **domain** + **contract** + **patra-common** + core starter  
- **infra** → **domain** + **contract** + mybatis starter + core starter
- **domain** → **仅** **patra-common** (禁止Spring/框架)
- **api**, **contract** → 不依赖框架

### 分层职责
- **api**: 对外契约(RPC DTO, 事件, 枚举, 错误码)
- **adapter**: REST控制器, 调度器, MQ生产消费者  
- **app**: 用例编排, 权限事务, 聚合协作, 事件发布
- **domain**: 聚合根, 实体, 值对象, 领域事件, 仓储端口
- **infra**: 持久化实现, DO↔聚合转换, Outbox模式

---

## 3. 开发工作流

### 本地环境启动
```bash
# 启动基础设施 
cd docker/compose && docker-compose -f docker-compose.dev.yml up -d

# 服务端口: MySQL:13306 Redis:16379 ES:9200 Nacos:8848 SkyWalking:8088 XXL-Job:7070

# 构建命令
mvn clean compile                    # 全仓编译  
cd patra-registry && mvn clean compile  # 指定模块
mvn clean package -DskipTests       # 打包跳测试
```

### 数据库与配置模式
- **DO命名**: `{Service}{Table}DO` (如`RegProvenanceDO`)
- **JSON字段**: 统一用`JsonNode`，避免Java enum
- **MyBatis Mapper**: `extends BaseMapper<xxxDO>`，使用`@Select`注解SQL
- **时间/生效性**: 实体含`effective_from/effective_to`支持配置版本控制

---

## 4. 编码规范与约定

### Lombok & MapStruct 强制要求
- **所有POJO**: 使用`@Data/@Getter/@Setter/@Builder`等，禁止手写样板代码
- **不可变对象**: 优先`record`，需可变时用`class + Lombok`
- **MapStruct**: `@Mapper(componentModel="spring", unmappedTargetPolicy=IGNORE)`

### 命名约定
- **Controller**: `{Resource}Controller`  
- **应用服务**: `{Aggregate}AppService`
- **仓储**: `{Aggregate}Repository` / `{Aggregate}RepositoryMpImpl`
- **Mapper**: `{Entity}Mapper`, **Converter**: `{Entity}Converter`

### REST API模式
- **路径**: `/api/{service}/**`，资源用复数
- **命令操作**: 冒号后缀 (如`POST /provenances/{id}:sync`)

---

## 5. 错误处理与观测

### Patra错误处理系统
- **ErrorResolutionService**: 自动解析异常→错误码→HTTP状态
- **ProblemDetail**: RFC 7807标准响应格式，含traceId传播
- **配置**: 每服务需`patra.error.context-prefix` (如REG, ORD, INV)

### 日志与监控集成
- **SkyWalking**: 分布式追踪，自动传播traceId
- **统一日志**: `@Slf4j` + 参数化格式，避免敏感信息泄露
- **性能**: 避免N+1查询，优先批处理/分页/异步

---

## 6. 表达式引擎集成

### 核心组件
- **patra-expr-kernel**: 表达式AST解析与执行
- **patra-spring-boot-starter-expr**: 编译器与快照管理
- **注册中心集成**: 数据源配置/字段映射/渲染规则统一管理

### 使用模式
```java
CompileRequest request = CompileRequestBuilder
    .of(expression, ProvenanceCode.PUBMED)
    .forTask(TaskTypes.UPDATE)  
    .forOperation(OperationCodes.SEARCH)
    .build();
CompileResult result = compiler.compile(request);
```

---

## 7. 数据一致性与事务

### CQRS分离
- **Query侧**: contract层ReadModel，经QueryPort访问，优化查询性能
- **Command侧**: domain聚合处理，app层事务编排
- **时间线**: 配置表支持`effective_from/to`实现版本管理

### Outbox事件模式
- **领域事件**: domain层描述业务事实
- **集成事件**: api层定义外部契约  
- **发布链路**: DomainEvent → AppEvent → IntegrationEvent → MQ

---

## 8. 特殊约定与最佳实践

### JSON处理
- **数据库JSON列**: 统一用`JsonNode`类型，配置`JsonToJsonNodeTypeHandler`

### 工具类复用优先级
1. **Hutool** → 2. **patra-common** → 3. **patra-* starters** → 4. 自定义工具

### JavaDoc要求
- **作者标记**: `@author linqibin @since 0.1.0`
- **API文档**: 公共类/方法补全`@param/@return/@throws`
- **层级标识**: 标明CQRS角色与六边形架构位置

---

## 9. 安全与性能
- **凭据管理**: 禁止硬编码，统一环境变量/Nacos配置注入
- **SQL安全**: 全面参数化，使用MyBatis-Plus预防注入
- **限流熔断**: 外部调用必须设置超时/重试/熔断策略
- **批量优化**: 优先`List<>`批处理而非单条操作循环
