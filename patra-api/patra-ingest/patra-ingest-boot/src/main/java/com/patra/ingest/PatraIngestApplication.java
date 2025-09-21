package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Patra采集模块启动类
 *
 * @author linqibin
 * @since 0.1.0
 */
@EnableFeignClients(basePackages = {"com.patra.registry.api.rpc.client"})
@SpringBootApplication
public class PatraIngestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatraIngestApplication.class, args);
    }
}
