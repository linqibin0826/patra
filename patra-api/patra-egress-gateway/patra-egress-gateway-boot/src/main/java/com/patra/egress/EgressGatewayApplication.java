package com.patra.egress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Egress Gateway Application Entry point for the egress gateway microservice
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootApplication
public class EgressGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(EgressGatewayApplication.class, args);
  }
}
