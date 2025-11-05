/**
 * Patra API 网关服务主包。
 *
 * <p>本包包含 Patra 医学文献数据平台的 API 网关服务,基于 Spring Cloud Gateway 实现。
 * 网关作为系统的统一入口,负责将外部请求路由到相应的后端微服务,并提供服务发现、负载均衡、
 * 分布式追踪等基础设施功能。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li><strong>请求路由</strong>: 根据 URL 路径将请求转发到正确的微服务(patra-registry、patra-ingest、patra-storage 等)
 *   <li><strong>服务发现</strong>: 通过 Nacos 自动发现后端服务实例并维护路由表
 *   <li><strong>负载均衡</strong>: 使用 Spring Cloud LoadBalancer 在多个服务实例间分配请求
 *   <li><strong>统一入口</strong>: 为所有 Patra API 提供单一访问点,简化客户端配置
 *   <li><strong>分布式追踪</strong>: 集成链路追踪,记录请求完整调用链以便问题诊断
 * </ul>
 *
 * <h2>组件说明</h2>
 *
 * <ul>
 *   <li>{@link com.patra.gateway.PatraGatewayApplication} - Spring Boot 应用启动类,网关服务主入口
 * </ul>
 *
 * <h2>路由配置</h2>
 *
 * 所有路由规则定义在 {@code application.yml} 中,遵循以下模式:
 *
 * <pre>{@code
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: patra-registry              # 路由唯一标识符
 *           uri: lb://patra-registry        # 目标服务(lb:// 表示负载均衡)
 *           predicates:
 *             - Path=/patra-registry/**     # 路径匹配规则
 *           filters:
 *             - StripPrefix=1               # 移除路径第一段
 * }</pre>
 *
 * <h2>路由示例</h2>
 *
 * <ul>
 *   <li><strong>Registry 服务</strong>:
 *       {@code GET /patra-registry/provenance/pubmed} →
 *       {@code lb://patra-registry/provenance/pubmed}
 *   <li><strong>Ingest 服务</strong>:
 *       {@code GET /patra-ingest/plans} →
 *       {@code lb://patra-ingest/plans}
 *   <li><strong>Storage 服务</strong>:
 *       {@code POST /patra-storage/internal/storage/files/record} →
 *       {@code lb://patra-storage/internal/storage/files/record}
 * </ul>
 *
 * <h2>服务发现</h2>
 *
 * 网关通过 Nacos 进行服务发现,配置如下:
 *
 * <pre>{@code
 * spring:
 *   cloud:
 *     nacos:
 *       discovery:
 *         server-addr: ${NACOS_ADDR:127.0.0.1:8848}
 *         namespace: ${NACOS_NAMESPACE_ID:public}
 *         group: ${NACOS_DISCOVERY_GROUP:DEFAULT_GROUP}
 * }</pre>
 *
 * <h2>配置说明</h2>
 *
 * <ul>
 *   <li><strong>默认端口</strong>: 9528
 *   <li><strong>环境变量</strong>:
 *       <ul>
 *         <li>{@code NACOS_ADDR} - Nacos 服务器地址(默认: 127.0.0.1:8848)
 *         <li>{@code NACOS_NAMESPACE_ID} - Nacos 命名空间(默认: public)
 *         <li>{@code SPRING_PROFILES_ACTIVE} - 激活的配置文件(dev/prod)
 *       </ul>
 *   <li><strong>日志级别</strong>: 开发环境启用 DEBUG 级别以便调试路由和负载均衡
 * </ul>
 *
 * <h2>技术栈</h2>
 *
 * <ul>
 *   <li>Spring Boot 3.5.7
 *   <li>Spring Cloud Gateway 2025.0.0
 *   <li>Spring Cloud LoadBalancer (客户端负载均衡)
 *   <li>Nacos Discovery (服务发现和注册)
 *   <li>Nacos Config (动态配置管理)
 * </ul>
 *
 * <h2>运维指南</h2>
 *
 * <ul>
 *   <li><strong>启动服务</strong>: {@code java -jar patra-gateway-boot.jar}
 *   <li><strong>健康检查</strong>: {@code GET /actuator/health}
 *   <li><strong>路由信息</strong>: {@code GET /actuator/gateway/routes}
 *   <li><strong>查看注册服务</strong>: 访问 Nacos 控制台查看已注册的微服务实例
 * </ul>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>模块文档: {@code patra-gateway-boot/README.md}
 *   <li>Spring Cloud Gateway 官方文档: <a href="https://spring.io/projects/spring-cloud-gateway">spring.io/projects/spring-cloud-gateway</a>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.gateway;
