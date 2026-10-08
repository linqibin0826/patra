package dev.linqibin.patra.starter.security.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionClaims;
import dev.linqibin.patra.starter.security.assertion.IdentityAssertionSigner;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/// 测试用的签名密钥：本 JVM 第一次用到时生成一对 P-256 密钥，之后复用。
///
/// 私钥只在测试进程的内存里，不写文件，不进仓库。
public final class TestSigningKey {

  private static final ECKey KEY = generate();

  /// 工具类，不允许实例化。
  private TestSigningKey() {}

  /// 含私钥的 JWK。
  ///
  /// @return 密钥对
  public static ECKey key() {
    return KEY;
  }

  /// 只含公钥的 JWK Set JSON，给 `patra.security.identity-assertion.public-keys` 用。
  ///
  /// @return JWK Set JSON
  public static String publicJwkSet() {
    return new JWKSet(KEY.toPublicJWK()).toString(true);
  }

  /// 用测试私钥和系统时钟构造的签名器。
  ///
  /// @return 签名器
  public static IdentityAssertionSigner signer() {
    return new IdentityAssertionSigner(KEY, Clock.systemUTC());
  }

  /// 用测试私钥签任意载荷，头和签名器一致。给需要构造过期、内容不合法等异常断言的测试用。
  ///
  /// @param claims 载荷
  /// @return 紧凑序列化的断言
  public static String sign(JWTClaimsSet claims) {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(IdentityAssertionClaims.TYPE))
                .keyID(KEY.getKeyID())
                .build(),
            claims);
    try {
      jwt.sign(new ECDSASigner(KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException("签测试断言失败", e);
    }
    return jwt.serialize();
  }

  /// 合法载荷的模板：签名器会写的每个声明都在，值取自给定用户，现在签出、60 秒后过期。
  /// 测试改坏一处再调 `sign`。
  ///
  /// @param user 用户
  /// @return 载荷构建器
  public static JWTClaimsSet.Builder claims(CurrentUser user) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(IdentityAssertionClaims.ISSUER)
        .audience(IdentityAssertionClaims.AUDIENCE)
        .subject(Long.toString(user.userId()))
        .claim(IdentityAssertionClaims.SESSION_ID, Long.toString(user.sessionId()))
        .claim(IdentityAssertionClaims.ACCOUNT_TYPE, user.accountType().getCode())
        .claim(IdentityAssertionClaims.CLIENT_TYPE, user.clientType().getCode())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(IdentityAssertionClaims.LIFETIME)));
  }

  /// 生成带 kid（公钥指纹）的 P-256 密钥对。
  ///
  /// @return 密钥对
  private static ECKey generate() {
    try {
      return new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("生成测试密钥失败", e);
    }
  }
}
