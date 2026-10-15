package dev.patra.tool.launch;

import static dev.patra.tool.launch.NacosServerFixture.causeChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// 密码错误的集成测试（spec 8.2 第 5 条）。账号与其他用例不同，单独一个 JVM。
@DisplayName("Nacos 密码错误 集成测试")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class NacosWrongPasswordIT {

  private static NacosServerFixture nacos;

  @BeforeAll
  static void startNacos() {
    NacosServerFixture.isolateSnapshots();
    nacos = NacosServerFixture.start();
    nacos.useGrpcPortOffset();
    nacos.publish(
        "patra-common.yaml",
        """
        patra:
          config-source:
            common: patra-common.yaml
        """);
    nacos.publish(
        "patra-it-app.yaml",
        """
        patra:
          config-source:
            service: patra-it-app.yaml
        """);
  }

  @AfterAll
  static void stopNacos() {
    nacos.close();
  }

  @Test
  @DisplayName("密码错误：启动失败，原因是 Nacos 拒绝访问，不是签到检查")
  void should_fail_when_password_wrong() {
    assertThatThrownBy(() -> nacos.launch("patra-it-app", "wrong-password"))
        .satisfies(failure -> assertThat(causeChain(failure)).doesNotContain("没拿到"));
  }
}
