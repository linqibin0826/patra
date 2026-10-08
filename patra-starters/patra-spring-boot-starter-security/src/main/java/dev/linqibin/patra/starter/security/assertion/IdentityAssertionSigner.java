package dev.linqibin.patra.starter.security.assertion;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/// 身份断言的签名器。网关查完会话后用它签，测试支持用临时密钥构造它。
///
/// 不做成自动配置的 Bean：签名用的配置项只存在于网关自己的配置里，下游拿不到私钥。
public final class IdentityAssertionSigner {

  private final ECKey key;
  private final JWSSigner signer;
  private final Clock clock;

  /// 创建签名器。
  ///
  /// @param key 含私钥的 P-256 JWK，必须带 `kid`
  /// @param clock 时钟，决定 `iat` 和 `exp`
  /// @throws IllegalArgumentException 密钥不含私钥、没有 `kid` 或不是 P-256 时
  public IdentityAssertionSigner(ECKey key, Clock clock) {
    this.key = Objects.requireNonNull(key, "key 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
    if (!key.isPrivate()) {
      throw new IllegalArgumentException("签名密钥必须含私钥");
    }
    if (key.getKeyID() == null || key.getKeyID().isBlank()) {
      throw new IllegalArgumentException("签名密钥必须带 kid");
    }
    if (!Curve.P_256.equals(key.getCurve())) {
      throw new IllegalArgumentException("签名密钥必须是 P-256 曲线");
    }
    try {
      this.signer = new ECDSASigner(key);
    } catch (JOSEException e) {
      throw new IllegalArgumentException("签名密钥不可用", e);
    }
  }

  /// 为当前用户签一个断言。每次调用都现签，不缓存。
  ///
  /// @param user 当前用户
  /// @return 紧凑序列化的断言
  /// @throws IllegalStateException 签名失败时；密钥坏了是配置错误，不是请求错误
  public String sign(CurrentUser user) {
    Objects.requireNonNull(user, "user 不能为 null");
    Instant now = clock.instant();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(IdentityAssertionClaims.ISSUER)
            .audience(IdentityAssertionClaims.AUDIENCE)
            .subject(Long.toString(user.userId()))
            .claim(IdentityAssertionClaims.SESSION_ID, Long.toString(user.sessionId()))
            .claim(IdentityAssertionClaims.ACCOUNT_TYPE, user.accountType().getCode())
            .claim(IdentityAssertionClaims.CLIENT_TYPE, user.clientType().getCode())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(IdentityAssertionClaims.LIFETIME)))
            .build();
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType(IdentityAssertionClaims.TYPE))
            .keyID(key.getKeyID())
            .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("签身份断言失败", e);
    }
    return jwt.serialize();
  }
}
