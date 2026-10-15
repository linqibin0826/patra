package dev.patra.tool.launch;

import static dev.patra.tool.launch.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Nacos 不可达且没有快照的集成测试（spec 8.2 第 4 条）。地址与其他用例不同，单独一个 JVM。
@DisplayName("Nacos 不可达 集成测试")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class NacosUnreachableIT {

  @BeforeAll
  static void isolateSnapshots() {
    NacosServerFixture.isolateSnapshots();
  }

  @Test
  @DisplayName("Nacos 不可达且没有快照：启动失败，两份配置都报没拿到")
  void should_fail_with_signature_messages_when_nacos_unreachable_and_no_snapshot() {
    assertThatThrownBy(
            () ->
                PatraApplication.run(
                    "patra-it-app",
                    NacosITBootstrap.class,
                    "--spring.main.web-application-type=none",
                    "--PATRA_NACOS_ADDR=127.0.0.1:1",
                    "--NACOS_USERNAME=nacos",
                    "--NACOS_PASSWORD=unused"))
        .satisfies(
            failure ->
                assertThat(causeChain(failure))
                    .contains(
                        "没拿到 Nacos 配置 patra-common.yaml", "没拿到本服务的 Nacos 配置 patra-it-app.yaml"));
  }
}
