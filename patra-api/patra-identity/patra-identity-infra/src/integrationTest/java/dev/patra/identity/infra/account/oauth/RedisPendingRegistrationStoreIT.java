package dev.patra.identity.infra.account.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.tool.test.container.initializer.RedisContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/// RedisPendingRegistrationStore 集成测试：凭证只以哈希进键，记录按有效期过期。
@DisplayName("RedisPendingRegistrationStore 集成测试")
class RedisPendingRegistrationStoreIT {

  private static final PendingRegistration PENDING =
      PendingRegistration.builder()
          .provider(IdentityProvider.GITHUB)
          .subject("583231")
          .accountLabel("octocat")
          .suggestedUsername("octocat")
          .expiresAt(Instant.parse("2026-11-04T08:30:00Z"))
          .build();

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redis;

  /// 连上共享的 Redis 测试容器。
  @BeforeAll
  static void connect() {
    GenericContainer<?> container = RedisContainerInitializer.getRedisContainer();
    connectionFactory =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(6379)));
    connectionFactory.afterPropertiesSet();
    connectionFactory.start();
    redis = new StringRedisTemplate(connectionFactory);
  }

  /// 断开连接。
  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  private final RedisPendingRegistrationStore store =
      new RedisPendingRegistrationStore(redis, JsonMapper.builder().build());

  @Test
  @DisplayName("写入后能按凭证读回，键里只有凭证的哈希，带过期时间")
  void should_create_and_find_by_ticket() {
    String ticket = store.create(PENDING, Duration.ofMinutes(30));

    assertThat(store.find(ticket)).contains(PENDING);
    String key = "idn:oauth:pending:" + new EmailTokenSecret(ticket).hash();
    assertThat(redis.hasKey(key)).isTrue();
    assertThat(redis.keys("*")).allSatisfy(k -> assertThat(k).doesNotContain(ticket));
    assertThat(redis.getExpire(key)).isBetween(1700L, 1800L);
  }

  @Test
  @DisplayName("格式不对、不存在的凭证都读不到")
  void should_not_find_unknown_ticket() {
    assertThat(store.find(null)).isEmpty();
    assertThat(store.find("short")).isEmpty();
    assertThat(store.find("A".repeat(43))).isEmpty();
  }

  @Test
  @DisplayName("删掉后读不到")
  void should_delete() {
    String ticket = store.create(PENDING, Duration.ofMinutes(30));

    store.delete(ticket);

    assertThat(store.find(ticket)).isEmpty();
  }

  private static PendingCode pendingCode() {
    return PendingCode.builder()
        .username("chen-yu")
        .email("chen@example.com")
        .codeExpiresAt(Instant.parse("2026-11-04T08:10:00Z"))
        .nextResendAt(Instant.parse("2026-11-04T08:01:00Z"))
        .build();
  }

  private static final RegistrationCode CODE = new RegistrationCode("483920");
  private static final RegistrationCode WRONG = new RegistrationCode("000000");

  private String ticketWithCode() {
    String ticket = store.create(PENDING, Duration.ofMinutes(30));
    store.storeCode(ticket, pendingCode(), CODE, Duration.ofMinutes(10));
    return ticket;
  }

  @Test
  @DisplayName("验证码：能读回用户名和邮箱，Redis 里没有原文，带过期时间")
  void should_store_code_hashed() {
    String ticket = ticketWithCode();

    assertThat(store.findCode(ticket)).contains(pendingCode());
    String key = "idn:oauth:code:" + new EmailTokenSecret(ticket).hash();
    assertThat(redis.opsForHash().entries(key).values())
        .allSatisfy(value -> assertThat(value.toString()).isNotEqualTo("483920"));
    assertThat(redis.getExpire(key)).isBetween(500L, 600L);
  }

  @Test
  @DisplayName("验证码对：返回用户名和邮箱，只能用一次")
  void should_match_only_once() {
    String ticket = ticketWithCode();

    assertThat(store.verifyCode(ticket, CODE, 5))
        .isEqualTo(new CodeCheck.Matched("chen-yu", "chen@example.com"));
    assertThat(store.verifyCode(ticket, CODE, 5)).isInstanceOf(CodeCheck.NoActiveCode.class);
    assertThat(store.findCode(ticket)).isEmpty();
  }

  @Test
  @DisplayName("错 5 次作废：第 4 次还是不对，第 5 次作废，之后正确的码也进不去")
  void should_invalidate_after_max_attempts() {
    String ticket = ticketWithCode();

    for (int i = 0; i < 4; i++) {
      assertThat(store.verifyCode(ticket, WRONG, 5)).isInstanceOf(CodeCheck.Mismatch.class);
    }
    assertThat(store.verifyCode(ticket, WRONG, 5)).isInstanceOf(CodeCheck.NoActiveCode.class);
    assertThat(store.verifyCode(ticket, CODE, 5)).isInstanceOf(CodeCheck.NoActiveCode.class);
  }

  @Test
  @DisplayName("重发：旧码作废，错误次数重新算")
  void should_replace_old_code_on_resend() {
    String ticket = ticketWithCode();
    store.verifyCode(ticket, WRONG, 5);
    RegistrationCode next = new RegistrationCode("111111");

    store.storeCode(ticket, pendingCode(), next, Duration.ofMinutes(10));

    assertThat(store.verifyCode(ticket, CODE, 5)).isInstanceOf(CodeCheck.Mismatch.class);
    assertThat(store.verifyCode(ticket, next, 5)).isInstanceOf(CodeCheck.Matched.class);
  }

  @Test
  @DisplayName("没发过码：NoActiveCode；删掉待补填记录时验证码一起删")
  void should_have_no_code_when_not_sent_or_deleted() {
    String fresh = store.create(PENDING, Duration.ofMinutes(30));
    assertThat(store.verifyCode(fresh, CODE, 5)).isInstanceOf(CodeCheck.NoActiveCode.class);

    String ticket = ticketWithCode();
    store.delete(ticket);

    assertThat(store.findCode(ticket)).isEmpty();
  }
}
