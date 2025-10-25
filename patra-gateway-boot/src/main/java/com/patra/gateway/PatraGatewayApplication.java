package com.patra.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Papertrace API Gateway.
 *
 * <p>This Spring Cloud Gateway service provides unified routing and service discovery for all
 * Papertrace microservices. It handles request routing, load balancing via Nacos service discovery,
 * and serves as the single entry point for external clients.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Route requests to downstream microservices (patra-registry, patra-ingest)
 *   <li>Service discovery and load balancing via Nacos
 *   <li>Request/response logging with distributed tracing
 *   <li>CORS handling and global filters
 * </ul>
 *
 * <p>Default port: 9528
 *
 * @see org.springframework.cloud.gateway.route.RouteDefinition
 */
@SpringBootApplication
public class PatraGatewayApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(PatraGatewayApplication.class, args);
  }
}
