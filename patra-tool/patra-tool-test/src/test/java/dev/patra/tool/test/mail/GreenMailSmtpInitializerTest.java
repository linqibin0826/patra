package dev.patra.tool.test.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;

/// GreenMailSmtpInitializer 单元测试：JVM 内单例、随机端口、写进环境的属性、免鉴权收信与清空。
@DisplayName("GreenMailSmtpInitializer 单元测试")
class GreenMailSmtpInitializerTest {

  @Test
  @DisplayName("两次取到同一个实例，端口是启动后真实分配的")
  void should_start_once_and_expose_port() {
    GreenMail first = GreenMailSmtpInitializer.getGreenMail();

    assertThat(GreenMailSmtpInitializer.getGreenMail()).isSameAs(first);
    assertThat(GreenMailSmtpInitializer.getSmtpPort()).isPositive();
    assertThat(first.getSmtp().getPort()).isEqualTo(GreenMailSmtpInitializer.getSmtpPort());
  }

  @Test
  @DisplayName("把 spring.mail.host / port 和关掉鉴权、TLS 的属性写进环境")
  void should_write_mail_properties_into_environment() {
    try (GenericApplicationContext context = new GenericApplicationContext()) {
      new GreenMailSmtpInitializer().initialize(context);
      Environment env = context.getEnvironment();

      assertThat(env.getProperty("spring.mail.host")).isEqualTo("127.0.0.1");
      assertThat(env.getProperty("spring.mail.port", Integer.class))
          .isEqualTo(GreenMailSmtpInitializer.getSmtpPort());
      assertThat(env.getProperty("spring.mail.properties.mail.smtp.auth")).isEqualTo("false");
      assertThat(env.getProperty("spring.mail.properties.mail.smtp.ssl.enable")).isEqualTo("false");
      assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable"))
          .isEqualTo("false");
    }
  }

  @Test
  @DisplayName("不用鉴权就能收信；purge 清空全部邮箱")
  void should_accept_mail_without_authentication_and_purge() {
    GreenMail greenMail = GreenMailSmtpInitializer.getGreenMail();
    GreenMailSmtpInitializer.purge();

    GreenMailUtil.sendTextEmail(
        "to@example.com", "from@example.com", "探针", "正文", greenMail.getSmtp().getServerSetup());

    assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
    GreenMailSmtpInitializer.purge();
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }
}
