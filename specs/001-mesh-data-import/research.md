# Phase 0: 研究和调研

## 项目现有方案

基于代码库分析，以下是项目中已存在的相关技术方案：

| 技术点 | 现有实现 | 文件路径 | 是否复用 |
|--------|---------|---------|---------|
| MeSH Repository 框架 | 已有 MeshDescriptorRepositoryImpl 和相关 Mapper | patra-catalog-infra/persistence/repository/MeshDescriptorRepositoryImpl.java | ✅ 复用现有框架 |
| 定时任务调度 | XXL-Job 框架，有配置和任务基类 | patra-ingest-adapter/scheduler/config/XxlJobConfig.java | ✅ 复用 XXL-Job |
| XML 解析 | JAXB 解析 PubMed XML | patra-spring-boot-starter-provenance/pubmed/processor/ | ✅ 参考 JAXB 模式 |
| 批量导入模式 | PublicationMetadataDO 有 importBatch 字段 | patra-catalog-infra/persistence/entity/PublicationMetadataDO.java:58 | ✅ 参考批次管理模式 |
| MyBatis-Plus 批量插入 | 项目统一使用 MyBatis-Plus | patra-catalog-infra/persistence/mapper/* | ✅ 使用 saveBatch |
| 事务管理 | @Transactional 在 Application 层 | 项目通用模式 | ✅ 遵循项目规范 |

## 新研究决策

### 1. MeSH XML 数据解析方案

- **决策**：使用 StAX（Streaming API for XML）流式解析
- **理由**：
  - MeSH XML 文件很大（~500MB，35万条记录），必须流式处理
  - StAX 支持边读边处理，内存占用可控（<2GB）
  - **与 Jackson XML 的区别**：
    - Jackson XML 适合小文件（如 PubMed EFetch 几十条记录）
    - Jackson XML 会一次性加载整个 XML 到内存，然后映射到对象
    - StAX 是低层流式 API，可以逐元素读取，处理后立即释放内存
- **项目中是否已有**：
  - 是，项目在 patra-spring-boot-starter-provenance 中使用 Jackson XML 解析 PubMed 小文件
  - 但 MeSH 数据量大 100 倍，不适合用 Jackson XML
- **考虑的替代方案**：
  - Jackson XML（已否决：会一次性加载 500MB 到内存，导致 OOM）
  - JAXB（已否决：同样全量加载，内存占用过大）
  - DOM（已否决：同样存在内存问题）
  - SAX（可行但 StAX API 更友好，支持双向导航）

### 2. 断点续传实现方案

- **决策**：使用 MeshImportAggregate 聚合根 + TableProgress 值对象
- **理由**：
  - 符合 DDD 设计，聚合根管理任务生命周期
  - TableProgress 记录每张表的进度（已处理数/总数）
  - 批次号设计：表名_批次序号（如 descriptor_batch_001）
- **项目中是否已有**：否，但参考了 patra-ingest 的 TaskAggregate 模式
- **考虑的替代方案**：
  - Redis 存储进度（已否决：增加复杂性）
  - 文件系统标记（已否决：不够可靠）

### 3. 并发控制策略

- **决策**：单任务执行 + Redisson 分布式锁
- **理由**：
  - 避免并发导入造成数据混乱
  - 项目已集成 Redis，可直接使用 Redisson 分布式锁
  - Redisson 提供了开箱即用的分布式锁实现，支持锁续期和看门狗机制
  - 比数据库行锁性能更好，避免长时间锁表
- **项目中是否已有**：是，项目已集成 Redis，仅需添加 Redisson 依赖
- **考虑的替代方案**：
  - 数据库行锁（已否决：性能较差，长时间锁表影响其他操作）
  - ZooKeeper 锁（已否决：过于重量级）

### 4. 错误恢复机制

- **决策**：批次级别重试 + 失败批次记录
- **理由**：
  - 批次失败不影响其他批次，可继续处理
  - 失败批次记录详细错误信息，便于排查
  - 支持手动重试特定失败批次
- **项目中是否已有**：否，新设计
- **考虑的替代方案**：
  - 全量重新导入（已否决：效率太低）
  - 记录级别重试（已否决：管理复杂度高）

### 5. 数据验证策略

- **决策**：分层验证（XML Schema + 业务规则 + 数据完整性）
- **理由**：
  - XML Schema 验证确保格式正确
  - Domain 层验证业务规则（如 ID 格式、必填字段）
  - 导入完成后验证数量（±5% 容差）
- **项目中是否已有**：部分有（Domain 层验证），需扩展
- **考虑的替代方案**：
  - 仅业务规则验证（已否决：不够全面）
  - 外部验证服务（已否决：增加复杂性）

### 6. NLM 数据下载方案

- **决策**：使用 Spring RestClient（底层 JDK 21 HttpClient）
- **理由**：
  - 项目已在 patra-spring-boot-starter-provenance 中使用 RestClient
  - 底层使用 JDK 21 HttpClient，支持 HTTP/2、连接池、超时控制
  - 流式 API 简洁易用，支持响应流处理
  - 无需引入额外依赖，保持技术栈一致性
- **项目中是否已有**：是，已有完整的 RestClient 配置模板（参考 EPMCClientImpl）
- **考虑的替代方案**：
  - Apache HttpClient 5（已否决：引入新依赖，项目未使用）
  - OkHttp（已否决：同样引入新依赖）
  - 原生 URLConnection（已否决：功能有限）

### 7. 监控指标设计

- **决策**：Micrometer + Spring Boot Actuator + 自定义指标
- **理由**：
  - 项目已集成 Micrometer
  - 可通过 /actuator/metrics 端点查看
  - 自定义指标：任务耗时、批次处理速度、失败率
- **项目中是否已有**：是，基础设施已就绪
- **考虑的替代方案**：
  - Prometheus 直接集成（已有通过 Micrometer）
  - 自定义监控系统（已否决：重复造轮子）

## 技术风险评估

### 高风险
1. **XML 文件过大导致 OOM**
   - 缓解措施：使用 StAX 流式解析，设置 JVM 堆内存 -Xmx4G

2. **网络中断导致下载失败**
   - 缓解措施：支持断点续传，自动重试机制

### 中风险
1. **批次提交事务过大**
   - 缓解措施：动态调整批次大小，最大 2000 条/批

2. **数据格式变化**
   - 缓解措施：XML Schema 验证，版本化处理逻辑

### 低风险
1. **并发导入冲突**
   - 缓解措施：分布式锁 + XXL-Job 单机执行模式

2. **磁盘空间不足**
   - 缓解措施：预检查磁盘空间，下载后及时清理

## 依赖清单

### 需要新增的依赖

**在 patra-catalog-infra 模块的 pom.xml 中添加：**

```xml
<!-- Spring Web（仅用于 RestClient，不引入 Web 容器） -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <!-- 版本由 spring-boot-dependencies 管理 -->
</dependency>

<!-- Redisson（分布式锁，在 boot 模块添加） -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <!-- 版本由 patra-parent 管理 -->
</dependency>
```

### 已存在可复用的依赖
- **JDK 21 HttpClient**（RestClient 底层实现，JDK 内置）
- MyBatis-Plus（Infrastructure 层数据访问）
- XXL-Job（Adapter 层定时任务）
- Micrometer（监控指标）
- Hutool（工具类）
- Jackson（JSON 处理）
- Redis（已集成，用于缓存和分布式锁）

## 参考资源

1. **NLM MeSH 官方文档**
   - XML 格式说明：https://www.nlm.nih.gov/mesh/xmlmesh.html
   - 下载地址：https://nlmpubs.nlm.nih.gov/projects/mesh/

2. **StAX 教程**
   - Oracle 官方文档：https://docs.oracle.com/javase/tutorial/jaxp/stax/

3. **XXL-Job 官方文档**
   - GitHub：https://github.com/xuxueli/xxl-job

4. **项目内部参考**
   - patra-ingest 模块的任务管理模式（TaskAggregate、任务编排）
   - patra-spring-boot-starter-provenance 的 RestClient 使用模式（EPMCClientImpl）
   - patra-spring-boot-starter-provenance 的 XML 处理模式（PubMed JAXB）

## 结论

基于以上研究，技术方案已基本明确：
1. **复用项目现有的基础设施**
   - XXL-Job（任务调度）
   - Spring RestClient（HTTP 客户端，参考 patra-spring-boot-starter-provenance）
   - Redisson（分布式锁，Redis 已集成）
   - MyBatis-Plus（批量插入）
   - Micrometer（监控指标）

2. **核心技术选型**
   - StAX 流式 XML 解析（避免内存溢出）
   - MeshImportAggregate 聚合根（管理任务生命周期）
   - 批次级别的断点续传和错误恢复
   - Redisson 分布式锁（保证单任务执行）

3. **技术栈一致性与最小依赖原则**
   - 所有技术选择与项目现有实践保持一致
   - Infrastructure 层仅引入 `spring-web`，不引入 `spring-boot-starter-web`（避免 Web 容器）
   - Boot 层引入 `redisson-spring-boot-starter`（分布式锁，Redis 已有）
   - 参考 patra-ingest 和 patra-spring-boot-starter-provenance 的成熟模式

**所有需要澄清的问题已解决，可以进入 Phase 1 设计阶段。**