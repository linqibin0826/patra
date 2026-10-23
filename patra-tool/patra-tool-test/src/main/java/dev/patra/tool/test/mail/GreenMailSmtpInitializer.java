package dev.patra.tool.test.mail;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/// JVM 内的 SMTP 收件桩：GreenMail，随机端口、不要鉴权。
///
/// 同一个 JVM 里只启动一个实例，多个测试类共用；挂在 `@ContextConfiguration(initializers = …)` 上就把
/// `spring.mail.host` / `port` 写进环境，Boot 的 `MailSenderAutoConfiguration` 据此装配 `JavaMailSender`。
/// 不用 Spring 的测试直接 {@link #getGreenMail()} 取实例。用例之间用 {@link #purge()} 清空邮箱。
/// 用 GreenMail 而不是 Mailpit 容器：不拉镜像，能直接拿到 `MimeMessage` 断言 MIME 结构。
public class GreenMailSmtpInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger log = LoggerFactory.getLogger(GreenMailSmtpInitializer.class);

  private static final Object LOCK = new Object();

  private static final AtomicReference<GreenMail> INSTANCE = new AtomicReference<>();

  /// 返回已启动的 GreenMail，第一次调用时启动。
  ///
  /// @return GreenMail
  public static GreenMail getGreenMail() {
    synchronized (LOCK) {
      GreenMail current = INSTANCE.get();
      if (current == null) {
        current =
            new GreenMail(ServerSetupTest.SMTP.dynamicPort())
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());
        current.start();
        INSTANCE.set(current);
        log.info("GreenMail SMTP 已启动: 127.0.0.1:{}", current.getSmtp().getPort());
      }
      return current;
    }
  }

  /// SMTP 端口，启动后真实分配的。
  ///
  /// @return 端口
  public static int getSmtpPort() {
    return getGreenMail().getSmtp().getPort();
  }

  /// 清空全部邮箱，用例之间隔离。
  public static void purge() {
    try {
      getGreenMail().purgeEmailFromAllMailboxes();
    } catch (Exception e) {
      throw new IllegalStateException("清空 GreenMail 邮箱失败", e);
    }
  }

  /// 把 GreenMail 的地址写进 Spring 环境，并关掉鉴权和 TLS。
  ///
  /// @param applicationContext 正在初始化的上下文
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    int port = getSmtpPort();
    TestPropertyValues.of(
            "spring.mail.host=127.0.0.1",
            "spring.mail.port=" + port,
            "spring.mail.properties.mail.smtp.auth=false",
            "spring.mail.properties.mail.smtp.ssl.enable=false",
            "spring.mail.properties.mail.smtp.starttls.enable=false")
        .applyTo(applicationContext.getEnvironment());
  }
}
