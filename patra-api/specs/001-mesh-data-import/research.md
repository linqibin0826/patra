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
  - MeSH XML 文件很大（~35万条记录），避免一次性加载到内存
  - StAX 支持边读边处理，内存占用可控（<2GB）
  - 项目中虽然有 JAXB，但 JAXB 适合小文件，大文件会导致 OOM
- **考虑的替代方案**：
  - JAXB（已否决：内存占用过大）
  - DOM（已否决：同样存在内存问题）
  - SAX（可行但 StAX API 更友好）

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

- **决策**：单任务执行 + 分布式锁（基于数据库）
- **理由**：
  - 避免并发导入造成数据混乱
  - 使用数据库行锁（SELECT FOR UPDATE）实现简单可靠
  - XXL-Job 本身支持单机执行模式
- **项目中是否已有**：是，patra-ingest 有类似的任务并发控制
- **考虑的替代方案**：
  - Redisson 分布式锁（已否决：增加 Redis 依赖）
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

- **决策**：使用 Apache HttpClient 5 + 断点续传
- **理由**：
  - 支持 HTTP Range 请求，可断点续传
  - 连接池管理，性能可靠
  - 项目中已有 HttpClient 依赖
- **项目中是否已有**：是，Spring 内置 RestTemplate，但需要更精细控制
- **考虑的替代方案**：
  - OkHttp（可行但增加新依赖）
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
```xml
<!-- StAX API（JDK 自带，无需额外依赖） -->

<!-- Apache HttpClient 5（用于下载） -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <!-- 版本由 patra-parent 管理 -->
</dependency>
```

### 已存在可复用的依赖
- Spring Boot Starter Web
- MyBatis-Plus
- XXL-Job
- Micrometer
- Hutool（工具类）
- Jackson（JSON 处理）

## 参考资源

1. **NLM MeSH 官方文档**
   - XML 格式说明：https://www.nlm.nih.gov/mesh/xmlmesh.html
   - 下载地址：https://nlm.nih.gov/mesh/filelist.html

2. **StAX 教程**
   - Oracle 官方文档：https://docs.oracle.com/javase/tutorial/jaxp/stax/

3. **XXL-Job 官方文档**
   - GitHub：https://github.com/xuxueli/xxl-job

4. **项目内部参考**
   - patra-ingest 模块的任务管理模式
   - patra-spring-boot-starter-provenance 的 XML 处理

## 结论

基于以上研究，技术方案已基本明确：
1. 复用项目现有的基础设施（XXL-Job、MyBatis-Plus、Micrometer）
2. 采用流式 XML 解析避免内存问题
3. 设计清晰的聚合根边界管理任务生命周期
4. 实现批次级别的断点续传和错误恢复

**所有需要澄清的问题已解决，可以进入 Phase 1 设计阶段。**