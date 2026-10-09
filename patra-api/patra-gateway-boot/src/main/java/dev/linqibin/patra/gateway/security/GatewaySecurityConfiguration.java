package dev.linqibin.patra.gateway.security;

import com.nimbusds.jose.jwk.ECKey;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.identity.session.RedisSessionStore;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import java.text.ParseException;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/// 网关鉴权的装配：会话存储、签名器、两条过滤器链、出站头过滤器。
///
/// 设计：`docs/patra/specs/2026-10-09-gateway-auth-design.md`。
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayIdentityAssertionProperties.class)
public class GatewaySecurityConfiguration {

  /// Redis 里的会话存储，和 identity 共用同一份契约；网关只查和续期。
  ///
  /// @param redis Redis 模板，Boot 按 `spring.data.redis.*` 装配
  /// @param clock 容器里的时钟（starter-core 提供）
  /// @return 存储
  @Bean
  public RedisSessionStore redisSessionStore(StringRedisTemplate redis, Clock clock) {
    return new RedisSessionStore(redis, clock);
  }

  /// 安全异常到错误码的映射：starter 的表加一行查会话失败 → 0500。starter 的同类 Bean 随之让位。
  ///
  /// @param http 按网关前缀生成错误码的组
  /// @return 映射
  @Bean
  public GatewaySecurityErrorMappingContributor gatewaySecurityErrorMappingContributor(
      HttpStdErrors.Group http) {
    return new GatewaySecurityErrorMappingContributor(http);
  }

  /// 身份断言的签名器：解析私钥并自签自验一次，密钥配错在启动时就暴露。
  ///
  /// @param properties 私钥配置
  /// @param clock 容器里的时钟
  /// @param identityAssertionDecoder starter 用网关公钥建的验签器
  /// @return 签名器
  @Bean
  public IdentityAssertionSigner identityAssertionSigner(
      GatewayIdentityAssertionProperties properties,
      Clock clock,
      @Qualifier("identityAssertionDecoder") JwtDecoder identityAssertionDecoder) {
    return createSigner(properties.privateKey(), clock, identityAssertionDecoder);
  }

  /// 解析私钥、构造签名器、自检。
  ///
  /// 错误信息只出现配置项名和 `kid`，不出现密钥内容。
  ///
  /// @param privateKey 配置值，含私钥的 EC P-256 JWK JSON
  /// @param clock 时钟
  /// @param decoder 用网关公钥建的验签器
  /// @return 签名器
  /// @throws IllegalStateException 没配、不是 JWK、密钥不可用、和公钥不配对时
  static IdentityAssertionSigner createSigner(String privateKey, Clock clock, JwtDecoder decoder) {
    String property = GatewayIdentityAssertionProperties.PRIVATE_KEY_PROPERTY;
    if (privateKey == null || privateKey.isBlank()) {
      throw new IllegalStateException(
          "配置项 " + property + " 不能为空：它是网关签身份断言的私钥（含私钥的 EC P-256 JWK JSON）");
    }
    ECKey key;
    try {
      key = ECKey.parse(privateKey);
    } catch (ParseException e) {
      throw new IllegalStateException("配置项 " + property + " 不是合法的 EC JWK JSON", e);
    }
    IdentityAssertionSigner signer;
    try {
      signer = new IdentityAssertionSigner(key, clock);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("配置项 " + property + " 不可用：" + e.getMessage(), e);
    }
    CurrentUser probe = CurrentUser.of(1L, 1L, AccountType.USER, ClientType.WEB);
    try {
      decoder.decode(signer.sign(probe));
    } catch (JwtException e) {
      throw new IllegalStateException(
          "配置项 "
              + property
              + " 的私钥与 patra.security.identity-assertion.public-keys 里的公钥不配对或 kid 不一致（kid="
              + key.getKeyID()
              + "）",
          e);
    }
    log.info("身份断言签名密钥就绪，kid={}", key.getKeyID());
    return signer;
  }
}
