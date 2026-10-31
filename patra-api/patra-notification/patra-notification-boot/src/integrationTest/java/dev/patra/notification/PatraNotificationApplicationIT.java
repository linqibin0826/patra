package dev.patra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
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

/// notification 应用能启动：健康端点 UP、错误码前缀正确、测试里不起 XXL-Job 执行器。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("notification 应用启动")
class PatraNotificationApplicationIT {

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

  /// 错误码前缀是 `NOTIFICATION`。
  @Test
  void should_use_notification_error_prefix() {
    assertThat(environment.getProperty("patra.web.error.context-prefix")).isEqualTo("NOTIFICATION");
  }

  /// 测试 profile 关掉了执行器：漏关时上下文会因缺管理端地址启动失败，或在 6601 上起服务。
  @Test
  void should_not_start_xxl_job_executor_in_tests() {
    assertThat(context.getBeansOfType(XxlJobSpringExecutor.class)).isEmpty();
  }
}
