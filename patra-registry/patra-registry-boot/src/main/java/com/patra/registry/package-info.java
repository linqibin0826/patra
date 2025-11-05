/**
 * Patra Registry 启动入口包 - Spring Boot 应用启动和组装。
 *
 * <p>本包是 Registry 服务的可执行入口,负责组装所有六边形架构层(domain, app, adapter, infra)并启动 Spring Boot 应用。Registry 是 Patra 平台的单一真实数据源(SSOT),为其他微服务提供 Provenance、表达式、字典和元数据的集中管理。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>启动 Spring Boot 应用,作为独立微服务运行
 *   <li>组装六边形架构的所有模块(domain, app, adapter, infra)
 *   <li>加载 Nacos 配置中心的动态配置
 *   <li>注册到 Nacos 服务发现
 *   <li>暴露 REST API 供其他微服务 Feign 调用
 * </ul>
 *
 * <h2>架构定位</h2>
 *
 * <p>在六边形架构中,本模块位于最外层,负责应用组装和启动:
 *
 * <ul>
 *   <li><b>依赖关系</b>: boot → adapter → app → domain ← infra
 *   <li><b>Spring Boot 配置</b>: {@code @SpringBootApplication} 自动扫描所有层的 Bean
 *   <li><b>包扫描范围</b>: {@code com.patra.registry} 及其子包
 * </ul>
 *
 * <h2>服务特性</h2>
 *
 * <ul>
 *   <li><b>SSOT 定位</b>: Provenance、表达式、字典和元数据的单一事实来源
 *   <li><b>时态配置</b>: 支持配置的时间有效性管理(effective_from/until)
 *   <li><b>配置层次</b>: 支持 TASK 级覆盖 SOURCE 级的配置优先级
 *   <li><b>内部 RPC</b>: 通过 {@code /_internal/*} 端点供其他微服务 Feign 调用
 * </ul>
 *
 * <h2>主要 API 端点</h2>
 *
 * <h3>Provenance API</h3>
 *
 * <ul>
 *   <li>{@code GET /_internal/provenances} - 列出所有数据源
 *   <li>{@code GET /_internal/provenances/{code}} - 获取单个数据源
 *   <li>{@code GET /_internal/provenances/{code}/config} - 加载完整配置聚合
 * </ul>
 *
 * <h3>Expression API</h3>
 *
 * <ul>
 *   <li>{@code GET /_internal/expr/snapshot} - 获取完整表达式快照
 * </ul>
 *
 * <h2>依赖模块</h2>
 *
 * <ul>
 *   <li>{@code patra-registry-api} - 外部契约(Feign 客户端、DTO、错误码)
 *   <li>{@code patra-registry-domain} - 纯 Java 领域模型(无框架依赖)
 *   <li>{@code patra-registry-app} - 应用层,用例编排
 *   <li>{@code patra-registry-adapter} - 适配器层,REST 端点实现
 *   <li>{@code patra-registry-infra} - 基础设施层,持久化实现
 *   <li>{@code patra-spring-boot-starter-*} - Patra 统一的 Spring Boot 配置
 *   <li>{@code patra-common-core} - 共享枚举、工具类
 * </ul>
 *
 * <h2>启动配置</h2>
 *
 * <h3>application.yml</h3>
 *
 * <pre>{@code
 * spring:
 *   application:
 *     name: patra-registry
 *
 * server:
 *   port: 8081
 *
 * patra:
 *   logging:
 *     trace:
 *       enabled: true
 * }</pre>
 *
 * <h3>Nacos 配置</h3>
 *
 * <ul>
 *   <li>数据源连接信息(MySQL)</li>
 *   <li>MyBatis-Plus 配置</li>
 *   <li>日志级别</li>
 * </ul>
 *
 * <h2>启动方式</h2>
 *
 * <h3>本地开发</h3>
 *
 * <pre>{@code
 * # 进入 boot 模块
 * cd patra-registry-boot
 *
 * # 启动应用
 * ../../mvnw spring-boot:run
 * }</pre>
 *
 * <h3>生产部署</h3>
 *
 * <pre>{@code
 * # 构建 JAR
 * mvn clean package -DskipTests
 *
 * # 运行 JAR
 * java -jar patra-registry-boot/target/patra-registry-boot-*.jar
 * }</pre>
 *
 * <h2>技术栈</h2>
 *
 * <table border="1">
 *   <caption>Registry 服务技术栈</caption>
 *   <tr>
 *     <th>组件</th>
 *     <th>版本/说明</th>
 *   </tr>
 *   <tr>
 *     <td>Java</td>
 *     <td>25</td>
 *   </tr>
 *   <tr>
 *     <td>Spring Boot</td>
 *     <td>3.5.7</td>
 *   </tr>
 *   <tr>
 *     <td>Spring Cloud</td>
 *     <td>2025.0.0</td>
 *   </tr>
 *   <tr>
 *     <td>MyBatis-Plus</td>
 *     <td>持久化框架</td>
 *   </tr>
 *   <tr>
 *     <td>MapStruct</td>
 *     <td>对象映射</td>
 *   </tr>
 *   <tr>
 *     <td>Nacos</td>
 *     <td>服务注册与配置中心</td>
 *   </tr>
 *   <tr>
 *     <td>MySQL</td>
 *     <td>关系型数据库</td>
 *   </tr>
 *   <tr>
 *     <td>Flyway</td>
 *     <td>数据库版本管理</td>
 *   </tr>
 * </table>
 *
 * <h2>下游消费者</h2>
 *
 * <ul>
 *   <li><b>patra-ingest</b>: 通过 Feign 客户端查询数据源配置和表达式快照
 *   <li>其他微服务: 通过 {@code patra-registry-api} 模块引入客户端
 * </ul>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li><a href="../../README.md">patra-registry 模块 README</a></li>
 *   <li><a href="../patra-registry-api/README.md">patra-registry-api 模块</a></li>
 *   <li><a href="../patra-registry-domain/README.md">patra-registry-domain 模块</a></li>
 *   <li><a href="../patra-registry-app/README.md">patra-registry-app 模块</a></li>
 *   <li><a href="../patra-registry-adapter/README.md">patra-registry-adapter 模块</a></li>
 *   <li><a href="../patra-registry-infra/README.md">patra-registry-infra 模块</a></li>
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry;
