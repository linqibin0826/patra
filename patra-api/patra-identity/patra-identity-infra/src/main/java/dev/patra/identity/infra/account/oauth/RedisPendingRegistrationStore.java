package dev.patra.identity.infra.account.oauth;

import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import dev.patra.identity.infra.session.support.TransientRedisFailures;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/// 待补填记录的 Redis 实现：`idn:oauth:pending:<凭证哈希>`，值是 JSON；验证码 `idn:oauth:code:<凭证哈希>`，HASH。
///
/// 凭证是 32 字节随机数的 base64url，和邮件令牌同一种形状，所以复用 {@link EmailTokenSecret} 的生成、解析和哈希；
/// Redis 里只有哈希。
@Component
@RequiredArgsConstructor
public class RedisPendingRegistrationStore implements PendingRegistrationPort {

  private static final RedisScript<Long> STORE_CODE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/oauth-code-store.lua"), Long.class);

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> VERIFY_CODE_SCRIPT =
      RedisScript.of(new ClassPathResource("redis/oauth-code-verify.lua"), List.class);

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;
  private final SecureRandom random = new SecureRandom();

  /// 建一条待补填记录。
  ///
  /// @param registration 记录
  /// @param ttl 有效期
  /// @return 凭证原文
  @Override
  public String create(PendingRegistration registration, Duration ttl) {
    EmailTokenSecret ticket = EmailTokenSecret.generate(random);
    String json = jsonMapper.writeValueAsString(registration);
    execute(
        () -> {
          redis.opsForValue().set(pendingKey(ticket.hash()), json, ttl);
          return null;
        });
    return ticket.value();
  }

  /// 按凭证读。
  ///
  /// @param ticket 凭证原文，可以为 `null`
  /// @return 记录
  @Override
  public Optional<PendingRegistration> find(String ticket) {
    return EmailTokenSecret.parse(ticket)
        .flatMap(
            parsed ->
                Optional.ofNullable(
                    execute(() -> redis.opsForValue().get(pendingKey(parsed.hash())))))
        .map(json -> jsonMapper.readValue(json, PendingRegistration.class));
  }

  /// 写一个验证码，覆盖旧的。
  ///
  /// @param ticket 凭证原文
  /// @param pendingCode 绑定的用户名、邮箱和时间
  /// @param code 验证码
  /// @param ttl 有效期
  @Override
  public void storeCode(
      String ticket, PendingCode pendingCode, RegistrationCode code, Duration ttl) {
    String ticketHash = new EmailTokenSecret(ticket).hash();
    execute(
        () ->
            redis.execute(
                STORE_CODE_SCRIPT,
                List.of(codeKey(ticketHash)),
                pendingCode.username(),
                pendingCode.email(),
                code.hash(ticketHash),
                Long.toString(pendingCode.codeExpiresAt().toEpochMilli()),
                Long.toString(pendingCode.nextResendAt().toEpochMilli()),
                Long.toString(ttl.toMillis())));
  }

  /// 读发出去还没用掉的验证码。
  ///
  /// @param ticket 凭证原文
  /// @return 公开部分
  @Override
  public Optional<PendingCode> findCode(String ticket) {
    return EmailTokenSecret.parse(ticket)
        .flatMap(
            parsed -> {
              Map<Object, Object> fields =
                  execute(() -> redis.opsForHash().entries(codeKey(parsed.hash())));
              return fields.isEmpty() ? Optional.empty() : Optional.of(toPendingCode(fields));
            });
  }

  /// 原子地校验验证码。
  ///
  /// @param ticket 凭证原文
  /// @param code 提交的验证码
  /// @param maxAttempts 最多错几次
  /// @return 校验结果
  @Override
  public CodeCheck verifyCode(String ticket, RegistrationCode code, int maxAttempts) {
    String ticketHash = new EmailTokenSecret(ticket).hash();
    List<?> result =
        execute(
            () ->
                redis.execute(
                    VERIFY_CODE_SCRIPT,
                    List.of(codeKey(ticketHash)),
                    code.hash(ticketHash),
                    Integer.toString(maxAttempts)));
    return switch (String.valueOf(result.getFirst())) {
      case "MATCH" ->
          new CodeCheck.Matched(String.valueOf(result.get(1)), String.valueOf(result.get(2)));
      case "MISMATCH" -> new CodeCheck.Mismatch();
      default -> new CodeCheck.NoActiveCode();
    };
  }

  /// 删掉待补填记录和它的验证码。
  ///
  /// @param ticket 凭证原文
  @Override
  public void delete(String ticket) {
    EmailTokenSecret.parse(ticket)
        .ifPresent(
            parsed ->
                execute(
                    () ->
                        redis.delete(List.of(pendingKey(parsed.hash()), codeKey(parsed.hash())))));
  }

  /// HASH 转公开部分。
  ///
  /// @param fields 字段
  /// @return 公开部分
  private static PendingCode toPendingCode(Map<Object, Object> fields) {
    return PendingCode.builder()
        .username(String.valueOf(fields.get("username")))
        .email(String.valueOf(fields.get("email")))
        .codeExpiresAt(
            Instant.ofEpochMilli(Long.parseLong(String.valueOf(fields.get("expires_at")))))
        .nextResendAt(
            Instant.ofEpochMilli(Long.parseLong(String.valueOf(fields.get("next_resend_at")))))
        .build();
  }

  /// 验证码的键。
  ///
  /// @param ticketHash 凭证哈希
  /// @return 键
  static String codeKey(String ticketHash) {
    return "idn:oauth:code:" + ticketHash;
  }

  /// 待补填记录的键。
  ///
  /// @param ticketHash 凭证哈希
  /// @return 键
  static String pendingKey(String ticketHash) {
    return "idn:oauth:pending:" + ticketHash;
  }

  /// 执行 Redis 操作，暂时失败转成 503。
  ///
  /// @param operation 操作
  /// @param <T> 返回类型
  /// @return 操作的返回值
  private static <T> T execute(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (DataAccessException e) {
      if (TransientRedisFailures.isTransient(e)) {
        throw new TemporarilyUnavailableException(e);
      }
      throw e;
    }
  }
}
