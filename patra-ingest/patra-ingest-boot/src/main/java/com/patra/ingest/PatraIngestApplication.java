package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Patra ingest service
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
    // Default to 'dev' profile when no explicit profile is configured
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIngestApplication.class, args);
  }
}
