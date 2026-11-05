package dev.patra.analytics.insight;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.infra.config.AnalyticsITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// ClickHouse 连不上时计数接口回 503（insight spec §4.3），门户据此少显示看过与读过全文；热读页只读 PG，照常 200。
///
/// 不挂 ClickHouse 容器，地址指向一个没人监听的端口；服务照常启动（建表迁移在后台退避重试）。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "patra.clickhouse.url=http://127.0.0.1:1",
      "patra.clickhouse.username=default",
      "patra.clickhouse.password="
    })
@AutoConfigureRestTestClient
@ContextConfiguration(
    initializers = {
      AnalyticsITPostgreSQLContainerInitializer.class,
      RedisContainerInitializer.class
    })
@ActiveProfiles("test")
class InsightClickHouseDownIT {

  @Autowired private RestTestClient restClient;

  @Test
  void stats_are_503_when_clickhouse_is_down() {
    restClient
        .post()
        .uri("/articles/stats")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"articleIds\":[\"424242424242\"]}")
        .exchange()
        .expectStatus()
        .isEqualTo(503);
  }

  @Test
  void hot_reads_do_not_need_clickhouse() {
    assertThat(
            restClient
                .get()
                .uri("/hot-reads")
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus()
                .value())
        .isEqualTo(200);
  }
}
