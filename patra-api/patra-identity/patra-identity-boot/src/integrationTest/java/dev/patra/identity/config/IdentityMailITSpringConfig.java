package dev.patra.identity.config;

import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.infra.account.mail.SmtpEmailDeliveryAdapter;
import dev.patra.identity.infra.account.mail.ThymeleafMailRenderer;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 端到端测试的发信：仍然异步（「找回密码不泄露邮箱是否注册」靠它让两条路径耗时相近），
/// 但执行器交给 {@link MailDrain}，每个测试方法结束后由 {@link IdentityMailDrainTestExecutionListener}
/// 等在途邮件发完，再轮到下一个测试 `purge`。
///
/// 不这样做时，上一个测试还在路上的邮件会落进下一个测试清空后的收件箱，按主题或总数取邮件的断言就会拿错
/// （CI 机器慢时才出现）。各上下文共用一个 GreenMail，所以每个起应用的测试都要导入本配置。
@TestConfiguration(proxyBeanMethods = false)
public class IdentityMailITSpringConfig {

  /// 测试发信用的执行器与「等它空闲」。
  ///
  /// @return 可等待空闲的执行器
  @Bean
  public MailDrain identityMailDrain() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("mail-it-");
    executor.setVirtualThreads(true);
    executor.setCorePoolSize(IdentityConfiguration.MAIL_MAX_CONCURRENCY);
    executor.setMaxPoolSize(IdentityConfiguration.MAIL_MAX_CONCURRENCY);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return new MailDrain(executor);
  }

  /// 用上面执行器发信的适配器，取代生产装配的那个。
  ///
  /// @param mailSender Boot 装配的发信器
  /// @param renderer 模板渲染器
  /// @param properties 配置，取发件人
  /// @param drain 执行器
  /// @return 适配器
  @Bean
  @Primary
  public EmailDeliveryPort drainableEmailDeliveryPort(
      JavaMailSender mailSender,
      ThymeleafMailRenderer renderer,
      IdentityProperties properties,
      MailDrain drain) {
    return new SmtpEmailDeliveryAdapter(
        mailSender, renderer, properties.mail().from(), drain.executor());
  }

  /// 发信执行器，以及等它把已提交的邮件全部发完。
  ///
  /// @param executor 执行器
  public record MailDrain(ThreadPoolTaskExecutor executor) implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /// 等到已提交的发信任务全部完成；超时说明有邮件卡住，直接失败而不是让下一个测试拿错邮件。
    public void awaitIdle() {
      ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
      long deadline = System.nanoTime() + TIMEOUT.toNanos();
      while (pool.getCompletedTaskCount() < pool.getTaskCount()) {
        if (System.nanoTime() > deadline) {
          throw new IllegalStateException("等在途邮件发完超时：" + TIMEOUT);
        }
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("等在途邮件时被中断", e);
        }
      }
    }

    /// 上下文关闭时停掉执行器。
    @Override
    public void close() {
      executor.shutdown();
    }
  }
}
