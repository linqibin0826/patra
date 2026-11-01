package dev.patra.analytics.collect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.kafka.bootstrap-servers=127.0.0.1:1")
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      ClickHouseContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class CollectKafkaUnavailableIT {

  @Autowired private RestTestClient restClient;

  @Test
  void kafka_unavailable_returns_503_quickly() {
    String body =
        CollectExamples.batch(CollectExamples.example("collect-envelope--article-viewed.json"));

    long started = System.nanoTime();
    restClient
        .post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ANALYTICS-0503");
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
  }
}
