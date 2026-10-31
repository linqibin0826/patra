package dev.patra.notification.infra.mail.delivery;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.enums.MailDeliveryFailure;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/// 发信端口的 SMTP 实现：渲染文本 + HTML，组成 `multipart/alternative`，交给 Boot 装配的 `JavaMailSender`
/// 同步发出。线上的服务商是 Resend，本机与 e2e 是 Mailpit；代码里只有幂等键这一个 Resend 专属的头。
///
/// 先写调用方的附加头，再写固定的头（调用方覆盖不了）：`Resend-Idempotency-Key`（投递 ID，同一封信每次重试
/// 都一样，Resend 24 小时内只送达一次）、`Auto-Submitted: auto-generated` 与 `X-Auto-Response-Suppress: All`
/// （抑制对方的自动回复）、`Message-ID: <投递 ID@发件人域名>`（Spring 的 `JavaMailSenderImpl` 会保留显式设过的值，
/// 重试时不变）。
///
/// 失败一律转成 {@link MailDeliveryFailedException}，按 spec 6.6 分三类，只带 {@link #describe} 写好的脱敏原因、
/// 不挂原始异常；不打日志，由调用方打。交给 `JavaMailSender` 之前就失败的（收件地址不合法、模板渲染失败）
/// 是信本身的问题，判永久失败；之后的按 {@link #classify} 判。
public final class SmtpMailDeliveryAdapter implements MailDeliveryPort {

  /// Resend 认的幂等键头。
  static final String IDEMPOTENCY_KEY_HEADER = "Resend-Idempotency-Key";

  /// 失败原因的最大长度。
  static final int REASON_MAX_LENGTH = 500;

  /// 鉴权失败的 SMTP 回复码：配置问题，每封都会失败，判临时失败。
  private static final Set<Integer> AUTHENTICATION_CODES = Set.of(530, 534, 535);

  /// SMTP 回复开头的三位回复码。
  private static final Pattern REPLY_CODE = Pattern.compile("^\\s*([2-5]\\d\\d)(?:[ \\-]|$)");

  /// 邮箱地址：`@` 两侧都不含空白、尖括号与引号。
  private static final Pattern EMAIL = Pattern.compile("[^\\s<>\"'@]+@[^\\s<>\"']+");

  private final JavaMailSender mailSender;
  private final ThymeleafMailRenderer renderer;
  private final InternetAddress from;
  private final String messageIdDomain;

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
    this.messageIdDomain =
        this.from.getAddress().substring(this.from.getAddress().indexOf('@') + 1);
  }

  /// 渲染并同步发出一封信。
  ///
  /// @param mail 要发的信
  /// @throws MailDeliveryFailedException 组信或发送失败，按 spec 6.6 分类
  @Override
  public void deliver(OutgoingMail mail) {
    Objects.requireNonNull(mail, "mail 不能为 null");
    MimeMessage message;
    try {
      message = compose(mail);
    } catch (MessagingException | RuntimeException e) {
      throw new MailDeliveryFailedException(mail, MailDeliveryFailure.REJECTED, describe(e));
    }
    try {
      mailSender.send(message);
    } catch (RuntimeException e) {
      throw new MailDeliveryFailedException(mail, classify(e), describe(e));
    }
  }

  /// 组一封信：渲染、发件人、收件人、主题、文本 + HTML，先写附加头再写固定头。
  ///
  /// @param mail 要发的信
  /// @return 组好的信
  /// @throws MessagingException 地址不合法等
  private MimeMessage compose(OutgoingMail mail) throws MessagingException {
    RenderedMail rendered = renderer.render(mail);
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setValidateAddresses(true);
    helper.setFrom(from);
    helper.setTo(mail.to());
    helper.setSubject(rendered.subject());
    helper.setText(rendered.text(), rendered.html());
    for (Map.Entry<String, String> header : mail.headers().entrySet()) {
      message.setHeader(header.getKey(), header.getValue());
    }
    message.setHeader(IDEMPOTENCY_KEY_HEADER, mail.deliveryId().toString());
    message.setHeader("Auto-Submitted", "auto-generated");
    message.setHeader("X-Auto-Response-Suppress", "All");
    message.setHeader("Message-ID", "<" + mail.deliveryId() + "@" + messageIdDomain + ">");
    return message;
  }

  /// 把交给 `JavaMailSender` 之后的失败分类（spec 6.6）：沿 cause 链与 Spring 收集的各封失败逐个看。
  ///
  /// 依次：回复里有 `idempotent`（带 `concurrent` 为临时，否则为已接受过）；鉴权失败为临时；第一个 SMTP
  /// 回复码 4xx 为临时、5xx 为永久；链上有 IO 异常为临时；认不出的为临时。
  ///
  /// @param failure 发送失败
  /// @return 类别
  static MailDeliveryFailure classify(Throwable failure) {
    List<Throwable> chain = chainOf(failure);
    for (Throwable current : chain) {
      String message =
          current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
      if (message.contains("idempotent")) {
        return message.contains("concurrent")
            ? MailDeliveryFailure.TRANSIENT
            : MailDeliveryFailure.ALREADY_ACCEPTED;
      }
    }
    Integer code = null;
    for (Throwable current : chain) {
      if (current instanceof MailAuthenticationException
          || current instanceof AuthenticationFailedException) {
        return MailDeliveryFailure.TRANSIENT;
      }
      if (code == null && current.getMessage() != null) {
        Matcher matcher = REPLY_CODE.matcher(current.getMessage());
        if (matcher.find()) {
          code = Integer.valueOf(matcher.group(1));
        }
      }
    }
    if (code != null) {
      if (code >= 400 && code < 500 || AUTHENTICATION_CODES.contains(code)) {
        return MailDeliveryFailure.TRANSIENT;
      }
      if (code >= 500) {
        return MailDeliveryFailure.REJECTED;
      }
    }
    return MailDeliveryFailure.TRANSIENT;
  }

  /// 失败链上的全部异常：cause 链，加上 Spring `MailSendException` 收集的各封失败（各自再展开 cause 链）。
  ///
  /// @param failure 失败
  /// @return 按出现顺序、不重复
  private static List<Throwable> chainOf(Throwable failure) {
    List<Throwable> chain = new ArrayList<>();
    Deque<Throwable> pending = new ArrayDeque<>(List.of(failure));
    while (!pending.isEmpty()) {
      Throwable current = pending.removeFirst();
      if (chain.stream().anyMatch(seen -> seen == current)) {
        continue;
      }
      chain.add(current);
      if (current instanceof MailSendException send) {
        pending.addAll(List.of(send.getMessageExceptions()));
      }
      if (current.getCause() != null) {
        pending.add(current.getCause());
      }
    }
    return chain;
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
