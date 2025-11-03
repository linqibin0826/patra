package com.patra.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Papertrace API网关主入口。
 *
 * <p>Spring Cloud Gateway服务,为所有Papertrace微服务提供统一的路由和服务发现。 处理请求路由、通过Nacos服务发现进行负载均衡,并作为外部客户端的单一入口点。
 *
 * <p>核心职责:
 *
 * <ul>
 *   <li>将请求路由到下游微服务(patra-registry、patra-ingest等)
 *   <li>通过Nacos进行服务发现和负载均衡
 *   <li>请求/响应日志记录与分布式追踪
 *   <li>CORS处理和全局过滤器
 * </ul>
 *
 * <p>默认端口: 9528
 *
 * @see org.springframework.cloud.gateway.route.RouteDefinition
 */
@SpringBootApplication
public class PatraGatewayApplication {

  /**
   * 启动Spring Boot应用。
   *
   * @param args 传递给应用的命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(PatraGatewayApplication.class, args);
  }
}
