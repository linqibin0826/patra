package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Patra 采集服务的 Spring Boot 启动入口
 *
 * <p>Feign 客户端通过 {@code patra-spring-cloud-starter-feign} 自动发现,该启动器扫描所有匹配 {@code
 * com.patra.*.api.rpc.client} 的包。
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootApplication
public class PatraIngestApplication {

  public static void main(String[] args) {
    // 当未配置显式配置文件时,默认使用 'dev' 配置
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIngestApplication.class, args);
  }
}
