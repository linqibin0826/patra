package dev.patra.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import dev.patra.interaction.infra.config.InteractionITPostgreSQLContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

/// interaction 应用能启动：健康端点 UP、错误码前缀正确、测试里不起 XXL-Job 执行器。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = InteractionITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("interaction 应用启动")
class PatraInteractionApplicationIT {

  @Autowired private RestTestClient restClient;
  @Autowired private Environment environment;
  @Autowired private ApplicationContext context;

  /// 健康端点返回 UP。
  @Test
  void should_report_up_on_health_endpoint() {
    restClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  /// 错误码前缀是 `INTERACTION`。
  @Test
  void should_use_interaction_error_prefix() {
    assertThat(environment.getProperty("patra.web.error.context-prefix")).isEqualTo("INTERACTION");
  }

  /// 测试 profile 关掉了执行器：漏关时上下文会因缺管理端地址启动失败，或在 6501 上起服务。
  @Test
  void should_not_start_xxl_job_executor_in_tests() {
    assertThat(context.getBeansOfType(XxlJobSpringExecutor.class)).isEmpty();
  }
}
