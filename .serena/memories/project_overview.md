项目：Papertrace – 医学文献数据平台
目标：
- 采集 ≥10 个医学文献源（PubMed、EPMC 等），形成可靠落地链路（采集→解析/清洗→入库）。
- 以 SSOT（patra-registry）集中管理配置/词典/元数据。
- 后续提供检索与智能分析。
架构：微服务 + 六边形架构 + 领域驱动设计 + 事件驱动（异步通信）。
关键技术：Java 21、Maven 多模块、Spring Boot 3.2.4、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0、MyBatis-Plus、MySQL 8、Redis 7、Elasticsearch 8.14、RocketMQ 5.3.2、Nacos、SkyWalking、XXL-Job、Lombok、MapStruct、Hutool；自研 starters：patra-spring-boot-starter-core/-web/-mybatis、patra-spring-cloud-starter-feign；表达式引擎：patra-expr-kernel。
仓库结构（要点）：
- patra-parent（父 POM）
- patra-common（公共工具）
- patra-expr-kernel（表达式引擎）
- patra-gateway-boot（API 网关）
- patra-registry（SSOT 注册微服务）
- patra-ingest（采集/摄取微服务）
- patra-spring-boot-starter-*（自研 starters）
微服务通用子结构：{service}-boot/api/domain/app/infra/adapter；依赖方向：adapter→app+api；app→domain+common+core-starter；infra→domain+mybatis-starter+core-starter；domain 仅依赖 patra-common；api 对外暴露不依赖框架。
工程约束：小步变更、可回放/幂等/可观测；不在 domain 引入框架；不硬编码敏感信息（统一 Nacos/环境变量）；跨聚合最终一致；仓储按聚合整体持久化。
日志与文档：统一 SLF4J（@Slf4j），参数化日志，不打印敏感信息；JavaDoc 极简（作者/版本与关键 @param/@return）。