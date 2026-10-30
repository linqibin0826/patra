package dev.patra.notification.infra.mail.delivery;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/// 发信端口的 SMTP 实现：渲染文本 + HTML，组成 `multipart/alternative`，交给 Boot 装配的 `JavaMailSender`
/// 同步发出。线上的服务商是 Resend，本机与 e2e 是 Mailpit；代码里只有幂等键这一个 Resend 专属的头。
///
/// 每封带三个固定的头：`Resend-Idempotency-Key`（投递 ID，同一封信每次重试都一样，Resend 24 小时内只送达一次）、
/// `Auto-Submitted: auto-generated` 与 `X-Auto-Response-Suppress: All`（抑制对方的自动回复）。
/// 失败一律转成 {@link MailDeliveryFailedException}，只带 {@link #describe} 写好的脱敏原因、不挂原始异常；
/// 不打日志，由调用方打。
public final class SmtpMailDeliveryAdapter implements MailDeliveryPort {

  /// Resend 认的幂等键头。
  static final String IDEMPOTENCY_KEY_HEADER = "Resend-Idempotency-Key";

  /// 失败原因的最大长度。
  static final int REASON_MAX_LENGTH = 500;

  /// 邮箱地址：`@` 两侧都不含空白、尖括号与引号。
  private static final Pattern EMAIL = Pattern.compile("[^\\s<>\"'@]+@[^\\s<>\"']+");

  private final JavaMailSender mailSender;
  private final ThymeleafMailRenderer renderer;
  private final InternetAddress from;

  /// 创建适配器。
  ///
  /// @param mailSender Boot 装配的发信器
  /// @param renderer 模板渲染器
  /// @param from 发件人，形如 `Patra <no-reply@example.com>`，只能一个地址
  /// @throws IllegalArgumentException 发件人不是恰好一个合法地址
  public SmtpMailDeliveryAdapter(
      JavaMailSender mailSender, ThymeleafMailRenderer renderer, String from) {
    this.mailSender = Objects.requireNonNull(mailSender, "mailSender 不能为 null");
    this.renderer = Objects.requireNonNull(renderer, "renderer 不能为 null");
    this.from = parseFrom(from);
  }

  /// 渲染并同步发出一封信。
  ///
  /// @param mail 要发的信
  /// @throws MailDeliveryFailedException 渲染、组信或发送失败
  @Override
  public void deliver(OutgoingMail mail) {
    Objects.requireNonNull(mail, "mail 不能为 null");
    try {
      RenderedMail rendered = renderer.render(mail);
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setValidateAddresses(true);
      helper.setFrom(from);
      helper.setTo(mail.to());
      helper.setSubject(rendered.subject());
      helper.setText(rendered.text(), rendered.html());
      message.setHeader(IDEMPOTENCY_KEY_HEADER, mail.deliveryId().toString());
      message.setHeader("Auto-Submitted", "auto-generated");
      message.setHeader("X-Auto-Response-Suppress", "All");
      mailSender.send(message);
    } catch (MessagingException | RuntimeException e) {
      throw new MailDeliveryFailedException(mail, describe(e));
    }
  }

  /// 把失败写成一段不含邮箱地址的文字：沿 cause 链每层 `类名: 消息`，用 ` <- ` 连起来，
  /// 邮箱地址换成 `***`，最长 {@value #REASON_MAX_LENGTH} 个字符。SMTP 回复码与服务商的原文都留着。
  ///
  /// @param failure 失败
  /// @return 脱敏后的原因
  static String describe(Throwable failure) {
    StringBuilder reason = new StringBuilder();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (!reason.isEmpty()) {
        reason.append(" <- ");
      }
      reason.append(current.getClass().getSimpleName());
      if (current.getMessage() != null) {
        reason.append(": ").append(current.getMessage());
      }
      if (current.getCause() == current) {
        break;
      }
    }
    String masked = EMAIL.matcher(reason).replaceAll("***");
    return masked.length() <= REASON_MAX_LENGTH ? masked : masked.substring(0, REASON_MAX_LENGTH);
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
