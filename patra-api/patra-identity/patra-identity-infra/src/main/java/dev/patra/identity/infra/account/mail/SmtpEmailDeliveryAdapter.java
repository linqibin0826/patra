package dev.patra.identity.infra.account.mail;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 邮件发送端口的 SMTP 实现：渲染文本 + HTML，组成 `multipart/alternative`，交给 Boot 装配的
/// `JavaMailSender` 发出。
///
/// `send` 把工作交给执行器就返回，从不阻塞请求线程；渲染、发送中的任何失败只记 WARN，不抛给调用方，
/// 也不重试；队列满了这封丢掉、记 WARN。执行器由 {@link #async} 自己建（固定几条虚拟线程、有界队列、
/// 停机时等在途的几封）——不声明成 Bean：容器里多一个 `Executor` Bean 会让 Boot 的
/// `applicationTaskExecutor` 退让。每封邮件加
/// `Auto-Submitted: auto-generated` 与 `X-Auto-Response-Suppress: All`，抑制对方的自动回复。
/// 收件人与链接不进日志。
@Slf4j
public final class SmtpEmailDeliveryAdapter implements EmailDeliveryPort, AutoCloseable {

  /// 停机时等在途邮件发完的时长（毫秒）。
  static final long TERMINATION_TIMEOUT_MILLIS = 10_000;

  /// 排队等发的邮件上限：服务商长时间不通时不让内存无限涨，超出的丢掉并记 WARN。
  static final int QUEUE_CAPACITY = 1_000;

  private final JavaMailSender mailSender;
  private final ThymeleafMailRenderer renderer;
  private final InternetAddress from;
  private final Executor executor;

  /// 创建适配器。
  ///
  /// @param mailSender Boot 装配的发信器
  /// @param renderer 模板渲染器
  /// @param from 发件人，形如 `Patra <no-reply@example.com>`，只能一个地址
  /// @param executor 渲染和发送在它上面跑；传同步执行器就是同步发送
  /// @throws IllegalArgumentException 发件人不是一个合法地址
  public SmtpEmailDeliveryAdapter(
      JavaMailSender mailSender, ThymeleafMailRenderer renderer, String from, Executor executor) {
    this.mailSender = Objects.requireNonNull(mailSender, "mailSender 不能为 null");
    this.renderer = Objects.requireNonNull(renderer, "renderer 不能为 null");
    this.from = parseFrom(from);
    this.executor = Objects.requireNonNull(executor, "executor 不能为 null");
  }

  /// 带自己的异步执行器的适配器：固定 `maxConcurrency` 条虚拟线程、线程名前缀 `mail-`、
  /// 有界队列（{@link #QUEUE_CAPACITY}）、停机等 10 秒。提交永不阻塞：槽全占着就排队，队列满了就丢。
  /// 不用 `SimpleAsyncTaskExecutor` 的并发上限：它在提交时阻塞提交线程，服务商不通时会把请求线程拖住。
  ///
  /// @param mailSender Boot 装配的发信器
  /// @param renderer 模板渲染器
  /// @param from 发件人
  /// @param maxConcurrency 同时发送的上限
  /// @return 适配器
  public static SmtpEmailDeliveryAdapter async(
      JavaMailSender mailSender, ThymeleafMailRenderer renderer, String from, int maxConcurrency) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("mail-");
    executor.setVirtualThreads(true);
    executor.setCorePoolSize(maxConcurrency);
    executor.setMaxPoolSize(maxConcurrency);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationMillis(TERMINATION_TIMEOUT_MILLIS);
    executor.initialize();
    return new SmtpEmailDeliveryAdapter(mailSender, renderer, from, executor);
  }

  /// 异步发一封邮件，立即返回。执行器拒绝（队列满、已停机）时这封丢掉，只记 WARN。
  ///
  /// @param mail 要发的邮件
  /// @return 交给了执行器为 `true`；被拒绝为 `false`
  @Override
  public boolean send(OutgoingMail mail) {
    Objects.requireNonNull(mail, "mail 不能为 null");
    try {
      executor.execute(() -> deliver(mail));
      return true;
    } catch (RejectedExecutionException e) {
      log.warn("邮件发送队列已满或已停机，这封丢弃: template={}", mail.template(), e);
      return false;
    }
  }

  /// 关掉自己持有的执行器（若它可关），等在途的发完。
  @Override
  public void close() {
    if (executor instanceof ExecutorConfigurationSupport support) {
      support.shutdown();
    } else if (executor instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        log.warn("关闭发信执行器失败", e);
      }
    }
  }

  /// 渲染并发送；失败只记 WARN。
  ///
  /// @param mail 要发的邮件
  private void deliver(OutgoingMail mail) {
    try {
      RenderedMail rendered = renderer.render(mail);
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(mail.to().value());
      helper.setSubject(rendered.subject());
      helper.setText(rendered.text(), rendered.html());
      message.addHeader("Auto-Submitted", "auto-generated");
      message.addHeader("X-Auto-Response-Suppress", "All");
      mailSender.send(message);
      log.info("邮件已发送: template={}", mail.template());
    } catch (MessagingException | RuntimeException e) {
      log.warn("邮件发送失败: template={}", mail.template(), e);
    }
  }

  /// 解析并校验发件人：必须恰好一个合法地址。
  ///
  /// @param from 配置值
  /// @return 发件人地址
  private static InternetAddress parseFrom(String from) {
    Objects.requireNonNull(from, "from 不能为 null");
    try {
      InternetAddress[] parsed = InternetAddress.parse(from, true);
      if (parsed.length != 1) {
        throw new IllegalArgumentException("发件人必须是一个地址，实际有 " + parsed.length + " 个");
      }
      parsed[0].validate();
      return parsed[0];
    } catch (AddressException e) {
      throw new IllegalArgumentException("发件人不是合法的邮箱地址", e);
    }
  }
}
