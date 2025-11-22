/// Patra Registry 启动入口包 - Spring Boot 应用启动和组装。
/// 
/// 本包是 Registry 服务的可执行入口,负责组装所有六边形架构层(domain, app, adapter, infra)并启动 Spring Boot 应用。Registry 是
/// Patra 平台的单一真实数据源(SSOT),为其他微服务提供 Provenance、表达式、字典和元数据的集中管理。
/// 
/// ## 核心职责
/// 
/// - 启动 Spring Boot 应用,作为独立微服务运行
///   - 组装六边形架构的所有模块(domain, app, adapter, infra)
///   - 加载 Nacos 配置中心的动态配置
///   - 注册到 Nacos 服务发现
///   - 暴露 REST API 供其他微服务 Feign 调用
/// 
/// ## 架构定位
/// 
/// 在六边形架构中,本模块位于最外层,负责应用组装和启动:
/// 
/// - **依赖关系**: boot → adapter → app → domain ← infra
///   - **Spring Boot 配置**: `@SpringBootApplication` 自动扫描所有层的 Bean
///   - **包扫描范围**: `com.patra.registry` 及其子包
/// 
/// ## 服务特性
/// 
/// - **SSOT 定位**: Provenance、表达式、字典和元数据的单一事实来源
///   - **时态配置**: 支持配置的时间有效性管理(effective_from/until)
///   - **配置层次**: 支持 TASK 级覆盖 SOURCE 级的配置优先级
///   - **内部 RPC**: 通过 `/_internal/*` 端点供其他微服务 Feign 调用
/// 
/// ## 主要 API 端点
/// 
/// ### Provenance API
/// 
/// - `GET /_internal/provenances` - 列出所有数据源
///   - `GET /_internal/provenances/{code`} - 获取单个数据源
///   - `GET /_internal/provenances/{code`/config} - 加载完整配置聚合
/// 
/// ### Expression API
/// 
/// - `GET /_internal/expr/snapshot` - 获取完整表达式快照
/// 
/// ## 依赖模块
/// 
/// - `patra-registry-api` - 外部契约(Feign 客户端、DTO、错误码)
///   - `patra-registry-domain` - 纯 Java 领域模型(无框架依赖)
///   - `patra-registry-app` - 应用层,用例编排
///   - `patra-registry-adapter` - 适配器层,REST 端点实现
///   - `patra-registry-infra` - 基础设施层,持久化实现
///   - `patra-spring-boot-starter-*` - Patra 统一的 Spring Boot 配置
///   - `patra-common-core` - 共享枚举、工具类
/// 
/// ## 启动配置
/// 
/// ### application.yml
/// 
/// ```java
/// spring:
///   application:
///     name: patra-registry
/// 
/// server:
///   port: 8081
/// 
/// patra:
///   logging:
///     trace:
///       enabled: true
/// ```
/// 
/// ### Nacos 配置
/// 
/// - 数据源连接信息(MySQL)
///   - MyBatis-Plus 配置
///   - 日志级别
/// 
/// ## 启动方式
/// 
/// ### 本地开发
/// 
/// ```java
/// # 进入 boot 模块
/// cd patra-registry-boot
/// 
/// # 启动应用
/// ../../mvnw spring-boot:run
/// ```
/// 
/// ### 生产部署
/// 
/// ```java
/// # 构建 JAR
/// mvn clean package -DskipTests
/// 
/// # 运行 JAR
/// java -jar patra-registry-boot/target/patra-registry-boot-*.jar
/// ```
/// 
/// ## 技术栈
/// 
/// <table border="1">
///   <caption>Registry 服务技术栈</caption>
///   <tr>
///     <th>组件</th>
///     <th>版本/说明</th>
///   </tr>
///   <tr>
///     <td>Java</td>
///     <td>25</td>
///   </tr>
///   <tr>
///     <td>Spring Boot</td>
///     <td>3.5.7</td>
///   </tr>
///   <tr>
///     <td>Spring Cloud</td>
///     <td>2025.0.0</td>
///   </tr>
///   <tr>
///     <td>MyBatis-Plus</td>
///     <td>持久化框架</td>
///   </tr>
///   <tr>
///     <td>MapStruct</td>
///     <td>对象映射</td>
///   </tr>
///   <tr>
///     <td>Nacos</td>
///     <td>服务注册与配置中心</td>
///   </tr>
///   <tr>
///     <td>MySQL</td>
///     <td>关系型数据库</td>
///   </tr>
///   <tr>
///     <td>Flyway</td>
///     <td>数据库版本管理</td>
///   </tr>
/// </table>
/// 
/// ## 下游消费者
/// 
/// - **patra-ingest**: 通过 Feign 客户端查询数据源配置和表达式快照
///   - 其他微服务: 通过 `patra-registry-api` 模块引入客户端
/// 
/// ## 相关文档
/// 
/// - <a href="../../README.md">patra-registry 模块 README</a>
///   - <a href="../patra-registry-api/README.md">patra-registry-api 模块</a>
///   - <a href="../patra-registry-domain/README.md">patra-registry-domain 模块</a>
///   - <a href="../patra-registry-app/README.md">patra-registry-app 模块</a>
///   - <a href="../patra-registry-adapter/README.md">patra-registry-adapter 模块</a>
///   - <a href="../patra-registry-infra/README.md">patra-registry-infra 模块</a>
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry;
