/// Patra API 网关服务主包。
///
/// 本包包含 Patra 医学出版物数据平台的 API 网关服务,基于 Spring Cloud Gateway 实现。
/// 网关作为系统的统一入口,负责将外部请求路由到相应的后端微服务,并提供服务发现、负载均衡、 分布式追踪等基础设施功能。
///
/// ## 核心职责
///
/// - **请求路由**: 根据 URL 路径将请求转发到正确的微服务(patra-registry、patra-ingest、patra-object-storage 等)
///   - **服务发现**: 通过 Nacos 自动发现后端服务实例并维护路由表
///   - **负载均衡**: 使用 Spring Cloud LoadBalancer 在多个服务实例间分配请求
///   - **统一入口**: 为所有 Patra API 提供单一访问点,简化客户端配置
///   - **分布式追踪**: 集成链路追踪,记录请求完整调用链以便问题诊断
///
/// ## 组件说明
///
/// - {@link com.patra.gateway.PatraGatewayApplication} - Spring Boot 应用启动类,网关服务主入口
///
/// ## 路由配置
///
/// 所有路由规则定义在 `application.yml` 中,遵循以下模式:
///
/// ```java
/// spring:
///   cloud:
///     gateway:
///       routes:
///         - id: patra-registry              # 路由唯一标识符
///           uri: lb://patra-registry        # 目标服务(lb:// 表示负载均衡)
///           predicates:
///             - Path=/patra-registry# 路径匹配规则
///           filters:
///             - StripPrefix=1               # 移除路径第一段
/// ```
///
/// ## 路由示例
///
/// - **Registry 服务**: `GET /patra-registry/provenance/pubmed` →
// `lb://patra-registry/provenance/pubmed`
///   - **Ingest 服务**: `GET /patra-ingest/plans` → `lb://patra-ingest/plans`
///   - **Storage 服务**: `POST /patra-object-storage/internal/storage/files/record` →
///       `lb://patra-object-storage/internal/storage/files/record`
///
/// ## 服务发现
///
/// 网关通过 Nacos 进行服务发现,配置如下:
///
/// ```java
/// spring:
///   cloud:
///     nacos:
///       discovery:
///         server-addr: ${NACOS_ADDR:127.0.0.1:8848
///         namespace: ${NACOS_NAMESPACE_ID:public
///         group: ${NACOS_DISCOVERY_GROUP:DEFAULT_GROUP
/// ```
///
/// ## 配置说明
///
/// - **默认端口**: 9528
///   - **环境变量**:
///
/// - `NACOS_ADDR` - Nacos 服务器地址(默认: 127.0.0.1:8848)
///         - `NACOS_NAMESPACE_ID` - Nacos 命名空间(默认: public)
///         - `SPRING_PROFILES_ACTIVE` - 激活的配置文件(dev/prod)
///
///   - **日志级别**: 开发环境启用 DEBUG 级别以便调试路由和负载均衡
///
/// ## 技术栈
///
/// - Spring Boot 3.5.7
///   - Spring Cloud Gateway 2025.0.0
///   - Spring Cloud LoadBalancer (客户端负载均衡)
///   - Nacos Discovery (服务发现和注册)
///   - Nacos Config (动态配置管理)
///
/// ## 运维指南
///
/// - **启动服务**: `java -jar patra-gateway-boot.jar`
///   - **健康检查**: `GET /actuator/health`
///   - **路由信息**: `GET /actuator/gateway/routes`
///   - **查看注册服务**: 访问 Nacos 控制台查看已注册的微服务实例
///
/// ## 相关文档
///
/// - 模块文档: `patra-gateway-boot/README.md`
///   - Spring Cloud Gateway 官方文档: <a
///
// href="https://spring.io/projects/spring-cloud-gateway">spring.io/projects/spring-cloud-gateway</a>
///
/// @author linqibin
/// @since 0.1.0
package com.patra.gateway;
