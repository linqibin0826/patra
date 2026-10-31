package dev.patra.identity.config;

import dev.patra.identity.domain.account.policy.AccountDeletionPolicy;
import dev.patra.identity.domain.account.policy.DisplayNamePolicy;
import dev.patra.identity.domain.account.policy.EmailChangePolicy;
import dev.patra.identity.domain.account.policy.EmailResendPolicy;
import dev.patra.identity.domain.account.policy.LoginThrottlePolicy;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.policy.PasswordResetPolicy;
import dev.patra.identity.domain.account.policy.ProfileDisplayPolicy;
import dev.patra.identity.domain.account.policy.ProfilePolicy;
import dev.patra.identity.domain.account.policy.UsernameChangePolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.geolocation.IpGeolocationPort;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.password.CommonPasswordPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.useragent.UserAgentParsingPort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.policy.ReauthenticationPolicy;
import dev.patra.identity.domain.session.policy.SessionLifetime;
import dev.patra.identity.domain.session.policy.SessionLifetimePolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.identity.infra.account.avatar.AvatarStorageProperties;
import dev.patra.identity.infra.account.hashing.PasswordHashingAdapter;
import dev.patra.identity.infra.session.store.RedisSessionStore;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/// identity 的装配：把配置项变成领域层的规则对象，创建密码哈希适配器、会话策略、Redis 会话存储、签发领域服务、
/// 邮件链接规则、重发限流规则、令牌签发服务，以及再认证、找回密码、改邮箱的规则与守卫。安全邮件的投递端口由 infra 的
/// `OutboxEmailDeliveryAdapter` 自行注册，外送配置在 `IdentityEventsConfiguration`（PAP-104）。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  IdentityProperties.class,
  LoginThrottleProperties.class,
  AvatarStorageProperties.class
})
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

  /// 用户名规则：语法加保留字。
  ///
  /// @return 规则
  @Bean
  public UsernamePolicy usernamePolicy() {
    return new UsernamePolicy();
  }

  /// 昵称规则。
  ///
  /// @return 规则
  @Bean
  public DisplayNamePolicy displayNamePolicy() {
    return new DisplayNamePolicy();
  }

  /// 资料与隐私设置的字段规则，昵称部分复用昵称规则。
  ///
  /// @param displayNamePolicy 昵称规则
  /// @return 规则
  @Bean
  public ProfilePolicy profilePolicy(DisplayNamePolicy displayNamePolicy) {
    return new ProfilePolicy(displayNamePolicy);
  }

  /// 公开主页的展示规则：加入时间按 `patra.identity.mail.zone` 的时区换算到月。
  ///
  /// @param properties identity 配置
  /// @return 规则
  @Bean
  public ProfileDisplayPolicy profileDisplayPolicy(IdentityProperties properties) {
    return ProfileDisplayPolicy.of(ZoneId.of(properties.mail().zone()));
  }

  /// 改名策略：冷却与旧名保留期。不是正数时在这里让启动失败。
  ///
  /// @param properties 配置
  /// @return 策略
  @Bean
  public UsernameChangePolicy usernameChangePolicy(IdentityProperties properties) {
    IdentityProperties.Username username = properties.username();
    return UsernameChangePolicy.of(username.changeCooldown(), username.reservationPeriod());
  }

  /// 注销规则：把 `patra.identity.deletion.*` 变成领域的策略对象；非正数或提前量不短于冷静期时启动失败。
  ///
  /// @param properties identity 配置
  /// @return 规则
  @Bean
  public AccountDeletionPolicy accountDeletionPolicy(IdentityProperties properties) {
    IdentityProperties.Deletion deletion = properties.deletion();
    return AccountDeletionPolicy.of(deletion.coolingPeriod(), deletion.reminderLead());
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

  /// Redis 里的会话存储，只有 identity 读写（网关经内部接口查会话）。
  ///
  /// @param redis Redis 模板
  /// @param clock 时钟
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }

  /// 重发验证邮件的限流规则。不是正数时在这里让启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public EmailResendPolicy emailResendPolicy(IdentityProperties properties) {
    IdentityProperties.Resend resend = properties.mail().resend();
    return EmailResendPolicy.of(
        resend.cooldown(),
        resend.perAccountHourly(),
        resend.perAccountDaily(),
        resend.perIpHourly());
  }

  /// 邮件里站内链接的拼法。门户地址不是绝对的 http(s) 地址、五个路径不以 `/` 开头时启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public MailLinkPolicy mailLinkPolicy(IdentityProperties properties) {
    IdentityProperties.Links links = properties.mail().links();
    return MailLinkPolicy.builder()
        .baseUrl(links.baseUrl())
        .verifyEmailPath(links.verifyEmailPath())
        .securitySettingsPath(links.securitySettingsPath())
        .resetPasswordPath(links.resetPasswordPath())
        .confirmEmailChangePath(links.confirmEmailChangePath())
        .revertEmailChangePath(links.revertEmailChangePath())
        .build();
  }

  /// 签发一次性邮件令牌的领域服务，四种用途共用。
  ///
  /// @param tokens 令牌仓储
  /// @param clock 时钟
  /// @return 领域服务
  @Bean
  public EmailTokenIssuer emailTokenIssuer(EmailTokenRepository tokens, Clock clock) {
    return new EmailTokenIssuer(tokens, clock, new SecureRandom());
  }

  /// 再认证规则：窗口不是正数时启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public ReauthenticationPolicy reauthenticationPolicy(IdentityProperties properties) {
    return ReauthenticationPolicy.of(
        properties.reauth().window(), properties.reauth().rotationGrace());
  }

  /// 找回密码的限流规则。不是正数时启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public PasswordResetPolicy passwordResetPolicy(IdentityProperties properties) {
    IdentityProperties.PasswordReset reset = properties.passwordReset();
    return PasswordResetPolicy.of(
        reset.cooldown(), reset.perEmailDaily(), reset.perIpHourly(), reset.submitPerIpMinutely());
  }

  /// 改邮箱的限流规则。不是正数时启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public EmailChangePolicy emailChangePolicy(IdentityProperties properties) {
    return EmailChangePolicy.of(properties.emailChange().perAccountDaily());
  }

  /// 新设备登录提醒的规则（PAP-99）。不是正数时启动失败。
  ///
  /// @param properties 配置
  /// @return 规则
  @Bean
  public NewDeviceLoginPolicy newDeviceLoginPolicy(IdentityProperties properties) {
    return NewDeviceLoginPolicy.of(
        properties.newDeviceMail().lookback(), properties.newDeviceMail().cooldown());
  }

  /// 再认证守卫：按断言里的会话 ID 读会话的 `auth_at`。
  ///
  /// @param sessions 会话存储端口
  /// @param policy 再认证规则
  /// @param clock 时钟
  /// @return 守卫
  @Bean
  public ReauthenticationGuard reauthenticationGuard(
      SessionStorePort sessions, ReauthenticationPolicy policy, Clock clock) {
    return new ReauthenticationGuard(sessions, policy, clock);
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

  /// 撤销会话并结束登录记录的领域服务（PAP-99）。
  ///
  /// @param sessionStore 会话存储端口
  /// @param records 登录记录仓储
  /// @return 领域服务
  @Bean
  public SessionTerminator sessionTerminator(
      SessionStorePort sessionStore, UserLoginRecordRepository records) {
    return new SessionTerminator(sessionStore, records);
  }

  /// 把请求来源解析成设备名、端与属地的领域服务（PAP-99）。
  ///
  /// @param userAgents User-Agent 解析端口（Yauaa）
  /// @param geolocation IP 属地端口（ip2region）
  /// @return 领域服务
  @Bean
  public ClientOriginResolver clientOriginResolver(
      UserAgentParsingPort userAgents, IpGeolocationPort geolocation) {
    return new ClientOriginResolver(userAgents, geolocation);
  }
}
