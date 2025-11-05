/**
 * Registry REST API 契约根包 - 对外接口定义层。
 *
 * <p>本包是 patra-registry 服务的外部契约模块根包,定义了供其他微服务调用的 REST API 接口、
 * 数据传输对象(DTO)、Feign 客户端和错误码。本模块遵循"契约优先"设计原则,
 * 确保 API 接口的稳定性和向后兼容性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义 REST API 端点接口(OpenAPI 契约)
 *   <li>定义请求和响应数据传输对象(DTO)
 *   <li>提供 Feign 客户端接口,供下游服务集成
 *   <li>定义统一的错误码和异常响应结构
 *   <li>隔离内部领域模型与外部 API 表示
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@code endpoint} - REST API 端点接口契约
 *       <ul>
 *         <li>{@link com.patra.registry.api.endpoint.ProvenanceEndpoint} - 数据源 API 端点
 *         <li>{@link com.patra.registry.api.endpoint.ExprEndpoint} - 表达式 API 端点
 *       </ul>
 *   <li>{@code client} - Feign 客户端接口
 *       <ul>
 *         <li>{@link com.patra.registry.api.client.ProvenanceClient} - 数据源 Feign 客户端
 *         <li>{@link com.patra.registry.api.client.ExprClient} - 表达式 Feign 客户端
 *       </ul>
 *   <li>{@code dto} - 数据传输对象根包
 *       <ul>
 *         <li>{@link com.patra.registry.api.dto.provenance} - 数据源配置 DTOs
 *         <li>{@link com.patra.registry.api.dto.expr} - 表达式 DTOs
 *         <li>{@link com.patra.registry.api.dto.dict} - 字典 DTOs
 *       </ul>
 *   <li>{@link com.patra.registry.api.error} - 错误码和异常定义
 * </ul>
 *
 * <h2>核心 API 端点</h2>
 *
 * <ul>
 *   <li><strong>ProvenanceEndpoint</strong>:
 *       <ul>
 *         <li>GET {@code /_internal/provenances} - 列出所有数据源
 *         <li>GET {@code /_internal/provenances/{code}} - 获取单个数据源
 *         <li>GET {@code /_internal/provenances/{code}/config} - 加载完整配置聚合
 *       </ul>
 *   <li><strong>ExprEndpoint</strong>:
 *       <ul>
 *         <li>GET {@code /_internal/expr/snapshot} - 获取表达式快照
 *       </ul>
 * </ul>
 *
 * <h2>Feign 客户端集成</h2>
 *
 * <p>本模块提供开箱即用的 Feign 客户端,下游服务可直接引入依赖并启用:
 *
 * <pre>{@code
 * // 1. 添加 Maven 依赖
 * <dependency>
 *     <groupId>com.patra</groupId>
 *     <artifactId>patra-registry-api</artifactId>
 * </dependency>
 *
 * // 2. 启用 Feign 客户端
 * @SpringBootApplication
 * @EnableFeignClients(clients = {ProvenanceClient.class, ExprClient.class})
 * public class PatraIngestApplication {
 *     // ...
 * }
 *
 * // 3. 注入并使用
 * @Autowired
 * private ProvenanceClient provenanceClient;
 *
 * ProvenanceConfigResp config = provenanceClient.getConfiguration(
 *     ProvenanceCode.PUBMED,
 *     "HARVEST",
 *     Instant.now()
 * );
 * }</pre>
 *
 * <h2>时态查询支持</h2>
 *
 * <p>所有配置查询 API 都支持时态查询,通过 {@code at} 参数指定查询时间点:
 *
 * <ul>
 *   <li>查询在指定时刻生效的配置({@code effectiveFrom <= at < effectiveTo})
 *   <li>支持配置的安全更新,不影响正在运行的任务
 *   <li>提供配置审计和历史回溯能力
 *   <li>支持基于时间的 A/B 测试和渐进式发布
 * </ul>
 *
 * <h2>配置作用域优先级</h2>
 *
 * <p>配置按作用域分为三级,API 查询时自动应用优先级规则:
 *
 * <ul>
 *   <li><strong>TASK 级</strong>: 任务特定配置,最高优先级
 *   <li><strong>OPERATION 级</strong>: 操作类型特定配置(HARVEST/UPDATE/BACKFILL)
 *   <li><strong>SOURCE 级</strong>: 数据源默认配置,最低优先级
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>契约稳定性</strong>: API 变更必须遵循语义化版本控制,避免破坏性变更
 *   <li><strong>纯契约模块</strong>: 仅包含接口、DTO 和注解,不包含业务逻辑
 *   <li><strong>依赖最小化</strong>: {@code spring-web} 和 {@code spring-cloud-openfeign-core} 使用 {@code provided} 作用域
 *   <li><strong>DTO 不可变</strong>: 所有 DTOs 使用 {@code record} 实现,确保数据传输安全
 *   <li><strong>向后兼容</strong>: DTO 字段添加必须保持向后兼容,避免删除或重命名字段
 * </ul>
 *
 * <h2>依赖关系</h2>
 *
 * <ul>
 *   <li><strong>上游依赖</strong>:
 *       <ul>
 *         <li>{@code patra-common-core} - 共享枚举和工具类
 *         <li>{@code jakarta.validation-api} - DTO 验证注解
 *         <li>{@code spring-web} (provided) - {@code @RequestMapping} 等注解
 *         <li>{@code spring-cloud-openfeign-core} (provided) - {@code @FeignClient} 注解
 *       </ul>
 *   <li><strong>下游消费者</strong>:
 *       <ul>
 *         <li>{@code patra-registry-adapter} - 实现端点接口
 *         <li>{@code patra-ingest} - 通过 Feign 客户端调用 Registry 服务
 *         <li>其他微服务 - 引入本模块以访问 Registry 服务
 *       </ul>
 * </ul>
 *
 * <h2>错误处理</h2>
 *
 * <p>所有 API 错误都遵循 RFC 7807 ProblemDetail 格式,错误码定义在
 * {@link com.patra.registry.api.error.RegistryErrorCode}:
 *
 * <ul>
 *   <li>{@code REG-0xxx} - 通用 HTTP 对齐错误(通过 {@code HttpStdErrors} 生成)
 *   <li>{@code REG-1xxx} - 领域或业务特定错误(目录中维护)
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.api;
