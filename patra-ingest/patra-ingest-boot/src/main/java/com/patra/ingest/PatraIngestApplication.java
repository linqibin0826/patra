package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Patra ingest service.
 *
 * <p>Feign clients are automatically discovered via {@code patra-spring-cloud-starter-feign} which
 * scans all packages matching {@code com.patra.*.api.rpc.client}.
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootApplication
public class PatraIngestApplication {

  public static void main(String[] args) {
    SpringApplication.run(PatraIngestApplication.class, args);
  }
}
