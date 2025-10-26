package com.patra.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the storage metadata service. */
@SpringBootApplication
public class PatraStorageApplication {

  public static void main(String[] args) {
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraStorageApplication.class, args);
  }
}
