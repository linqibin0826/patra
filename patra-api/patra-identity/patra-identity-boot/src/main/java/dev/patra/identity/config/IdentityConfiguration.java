package dev.patra.identity.config;

import dev.patra.identity.domain.policy.LoginThrottlePolicy;
import dev.patra.identity.domain.policy.PasswordPolicy;
import dev.patra.identity.domain.policy.SessionLifetime;
import dev.patra.identity.domain.policy.SessionLifetimePolicy;
import dev.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.port.password.CommonPasswordPort;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.session.SessionStorePort;
import dev.patra.identity.domain.service.SessionIssuer;
import dev.patra.identity.infra.adapter.hashing.PasswordHashingAdapter;
import dev.patra.identity.session.RedisSessionStore;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/// identity 的装配：把配置项变成领域层的规则对象，创建密码哈希适配器、会话策略、Redis 会话存储、签发领域服务。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({IdentityProperties.class, LoginThrottleProperties.class})
public class IdentityConfiguration {

  /// 登录失败限制的参数来源：每次调用都按配置的当前值构造，配置刷新后下一次登录即用上新值。
  ///
  /// @param properties 可在线刷新的配置
  /// @return 参数来源
  @Bean
  public Supplier<LoginThrottlePolicy> loginThrottlePolicy(LoginThrottleProperties properties) {
    return () ->
        LoginThrottlePolicy.of(
            properties.getMaxFailures(),
            properties.getWindow(),
            properties.getLockDuration(),
            properties.getInFlightTtl());
  }

  /// 密码哈希：Argon2id。
  ///
  /// @param properties 配置
  /// @return 哈希端口
  @Bean
  public PasswordHashingPort passwordHashingPort(IdentityProperties properties) {
    IdentityProperties.PasswordHashing hashing = properties.passwordHashing();
    return PasswordHashingAdapter.argon2(hashing.maxConcurrent(), hashing.waitTimeout());
  }

  /// 注册时的密码规则。
  ///
  /// @param commonPasswordPort 常见密码名单
  /// @return 规则
  @Bean
  public PasswordPolicy passwordPolicy(CommonPasswordPort commonPasswordPort) {
    return new PasswordPolicy(commonPasswordPort);
  }

  /// 会话策略：把 `patra.identity.session.*` 变成领域的策略对象。
  ///
  /// 缺本服务要签发的那一行（`user.web`）、不认识的键、有效期不合法，都在这里让启动失败，
  /// 不等到第一次登录才报错。
  ///
  /// @param properties 配置
  /// @return 策略
  @Bean
  public SessionLifetimePolicy sessionLifetimePolicy(IdentityProperties properties) {
    IdentityProperties.Session session = properties.session();
    Map<String, Map<String, IdentityProperties.Lifetime>> configured = session.lifetime();
    Map<ClientType, SessionLifetime> lifetimes = new EnumMap<>(ClientType.class);
    for (Map.Entry<String, Map<String, IdentityProperties.Lifetime>> byAccount :
        configured.entrySet()) {
      AccountType accountType =
          AccountType.fromCode(byAccount.getKey())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "patra.identity.session.lifetime 里的账号类型不认识: " + byAccount.getKey()));
      for (Map.Entry<String, IdentityProperties.Lifetime> byClient :
          byAccount.getValue().entrySet()) {
        ClientType clientType =
            ClientType.fromCode(byClient.getKey())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "patra.identity.session.lifetime."
                                + accountType.getCode()
                                + " 里的客户端类型不认识: "
                                + byClient.getKey()));
        IdentityProperties.Lifetime lifetime = byClient.getValue();
        if (lifetime == null || lifetime.idle() == null || lifetime.absolute() == null) {
          throw new IllegalStateException(
              "patra.identity.session.lifetime."
                  + accountType.getCode()
                  + "."
                  + clientType.getCode()
                  + " 要同时配 idle 和 absolute");
        }
        lifetimes.put(clientType, SessionLifetime.of(lifetime.idle(), lifetime.absolute()));
      }
    }
    if (!lifetimes.containsKey(ClientType.WEB)) {
      throw new IllegalStateException("缺少 patra.identity.session.lifetime.user.web");
    }
    return SessionLifetimePolicy.of(lifetimes, session.maxSessionsPerUser());
  }

  /// Redis 里的会话存储，和网关共用同一份契约。
  ///
  /// @param redis Redis 模板
  /// @param clock 时钟
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }

  /// 建会话的领域服务。
  ///
  /// @param records 登录记录仓储
  /// @param sessionStore 会话存储端口
  /// @param policy 会话策略
  /// @param clock 时钟
  /// @return 领域服务
  @Bean
  public SessionIssuer sessionIssuer(
      UserLoginRecordRepository records,
      SessionStorePort sessionStore,
      SessionLifetimePolicy policy,
      Clock clock) {
    return new SessionIssuer(records, sessionStore, policy, clock);
  }
}
