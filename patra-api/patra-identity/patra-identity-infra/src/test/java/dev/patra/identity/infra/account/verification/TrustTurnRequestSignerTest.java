package dev.patra.identity.infra.account.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// TRUSTTURN 的请求签名：期望值在测试里按文档的规范串独立算一遍。
@DisplayName("TrustTurnRequestSigner")
class TrustTurnRequestSignerTest {

  private static final String SECRET = "unit-test-signing-input";
  private static final long TIMESTAMP = 1_793_000_000_123L;
  private static final String NONCE = "AbCdEfGhIjKlMnOpQrStUvWx";
  private static final byte[] BODY =
      "{\"site_key\":\"s\",\"ticket\":\"t\",\"expected_action\":\"login\"}"
          .getBytes(StandardCharsets.UTF_8);

  @Test
  @DisplayName("按六行规范串做 HMAC-SHA256，带 v1= 前缀")
  void signs_canonical_string() throws GeneralSecurityException {
    String canonical =
        "TRUSTTURN-TICKET-CONSUME-V1\nPOST\n/api/tickets/consume\n"
            + TIMESTAMP
            + "\n"
            + NONCE
            + "\n"
            + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BODY));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String expected =
        "v1=" + HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));

    assertThat(TrustTurnRequestSigner.sign(SECRET, TIMESTAMP, NONCE, BODY)).isEqualTo(expected);
  }

  @Test
  @DisplayName("请求体变了，签名也变")
  void signature_changes_when_body_changes() {
    byte[] other = "{}".getBytes(StandardCharsets.UTF_8);

    assertThat(TrustTurnRequestSigner.sign(SECRET, TIMESTAMP, NONCE, other))
        .isNotEqualTo(TrustTurnRequestSigner.sign(SECRET, TIMESTAMP, NONCE, BODY));
  }
}
