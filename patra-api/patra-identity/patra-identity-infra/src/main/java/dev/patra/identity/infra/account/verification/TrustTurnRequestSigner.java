package dev.patra.identity.infra.account.verification;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/// TRUSTTURN 消费票据请求的签名。规范串六行：协议域、方法、路径、时间戳、nonce、请求体字节的 SHA-256 十六进制，
/// 用 `app_secret` 做 HMAC-SHA256。摘要绑定请求体，认证靠 HMAC。
final class TrustTurnRequestSigner {

  /// 接口路径，也是规范串的一行。
  static final String PATH = "/api/tickets/consume";

  private static final String DOMAIN = "TRUSTTURN-TICKET-CONSUME-V1";
  private static final String METHOD = "POST";
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /// 只有静态方法。
  private TrustTurnRequestSigner() {}

  /// 算签名。
  ///
  /// @param appSecret 签名密钥
  /// @param timestampMillis 13 位毫秒时间戳
  /// @param nonce 随机 nonce
  /// @param body 实际发出的请求体字节
  /// @return `v1=<hex>`
  static String sign(String appSecret, long timestampMillis, String nonce, byte[] body) {
    try {
      String bodyDigest =
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
      String canonical =
          String.join(
              "\n", DOMAIN, METHOD, PATH, String.valueOf(timestampMillis), nonce, bodyDigest);
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return "v1="
          + HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("JDK 缺少 HmacSHA256 或 SHA-256", e);
    }
  }
}
