package dev.linqibin.patra.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra API网关主入口。
///
/// Spring Cloud Gateway 服务，为所有 Patra 微服务提供统一的路由和服务发现。
/// 处理请求路由、通过 Consul 服务发现进行负载均衡，并作为外部客户端的单一入口点。
///
/// 核心职责:
///
/// - 将请求路由到下游微服务（patra-registry、patra-ingest 等）
/// - 通过 Consul 进行服务发现和负载均衡
/// - 请求/响应日志记录与分布式追踪
/// - CORS 处理和全局过滤器
///
/// 默认端口: 9528
///
/// @see org.springframework.cloud.gateway.route.RouteDefinition
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraGatewayApplication {

  /// 启动Spring Boot应用。
  ///
  /// @param args 传递给应用的命令行参数
  public static void main(String[] args) {
    SpringApplication.run(PatraGatewayApplication.class, args);
  }
}
