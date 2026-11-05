package dev.patra.identity.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.DynamicPropertyRegistry;

/// 用 WireMock 扮演三家平台：OIDC 平台（Google、ORCID）给 RSA 签名的 `id_token` 和公钥，GitHub 式平台给令牌和资料。
///
/// 服务器整个 JVM 只有一个：Spring 的上下文缓存记着它的端口，按测试类启停的话端口就对不上了。
public final class FakeProviderFixture {

  /// 所有用例共用的 access token 字面量：留痕检查按它搜索。
  public static final String ACCESS_TOKEN = "fake-provider-access-token-do-not-keep";

  /// 假平台的 OIDC 签发方。
  public static final String GOOGLE_ISSUER = "https://accounts.fake-google.test";

  /// 假平台的 OIDC 签发方。
  public static final String ORCID_ISSUER = "https://fake-orcid.test";

  /// 测试配置里的客户端 ID。
  public static final String GOOGLE_CLIENT_ID = "test-google-client";

  /// 测试配置里的客户端 ID。
  public static final String ORCID_CLIENT_ID = "test-orcid-client";

  private static final WireMockServer SERVER =
      new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));

  private static final AtomicInteger requestsDuringStartup = new AtomicInteger(-1);

  private static final RSAKey SIGNING_KEY = generateKey("fake-provider-key");

  private static final RSAKey FOREIGN_KEY = generateKey("fake-provider-key");

  static {
    SERVER.start();
  }

  /// 只有静态方法。
  private FakeProviderFixture() {}

  /// 把三家的端点指到 WireMock。
  ///
  /// @param registry 动态属性
  public static void registerEndpoints(DynamicPropertyRegistry registry) {
    String prefix = "patra.identity.oauth.providers.";
    registry.add(prefix + "google.token-uri", () -> SERVER.baseUrl() + "/google/token");
    registry.add(prefix + "google.jwk-set-uri", () -> SERVER.baseUrl() + "/google/jwks");
    registry.add(prefix + "google.issuer", () -> GOOGLE_ISSUER);
    registry.add(prefix + "github.token-uri", () -> SERVER.baseUrl() + "/github/token");
    registry.add(prefix + "github.user-info-uri", () -> SERVER.baseUrl() + "/github/user");
    registry.add(prefix + "orcid.token-uri", () -> SERVER.baseUrl() + "/orcid/token");
    registry.add(prefix + "orcid.jwk-set-uri", () -> SERVER.baseUrl() + "/orcid/jwks");
    registry.add(prefix + "orcid.issuer", () -> ORCID_ISSUER);
  }

  /// 清掉桩和请求记录，只留公钥。第一次调用时记下之前收到的请求数：Spring 上下文已经启动完了。
  public static void reset() {
    requestsDuringStartup.compareAndSet(-1, SERVER.getAllServeEvents().size());
    SERVER.resetAll();
    String jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
    SERVER.stubFor(get(urlPathEqualTo("/google/jwks")).willReturn(okJson(jwks)));
    SERVER.stubFor(get(urlPathEqualTo("/orcid/jwks")).willReturn(okJson(jwks)));
  }

  /// 应用启动期间平台收到的请求数：identity 启动时不能访问三家接口。
  ///
  /// @return 请求数
  public static int requestsDuringStartup() {
    return requestsDuringStartup.get();
  }

  /// 让 Google 的令牌端点回一个 `id_token`。
  ///
  /// @param subject 主体
  /// @param email 邮箱
  /// @param nonce 授权请求里的 `nonce`
  public static void stubGoogle(String subject, String email, String nonce) {
    stubToken(
        "/google/token",
        idToken(
            SIGNING_KEY,
            claims(GOOGLE_ISSUER, GOOGLE_CLIENT_ID, subject, nonce).claim("email", email).build()));
  }

  /// 让 ORCID 的令牌端点回一个 `id_token`。
  ///
  /// @param subject 主体
  /// @param nonce 授权请求里的 `nonce`
  public static void stubOrcid(String subject, String nonce) {
    stubToken(
        "/orcid/token",
        idToken(SIGNING_KEY, claims(ORCID_ISSUER, ORCID_CLIENT_ID, subject, nonce).build()));
  }

  /// 让 Google 的令牌端点回一个指定的 `id_token`，协议攻击用。
  ///
  /// @param idToken `id_token`
  public static void stubGoogleIdToken(String idToken) {
    stubToken("/google/token", idToken);
  }

  /// 让 Google 的令牌端点回一个错误或 500。
  ///
  /// @param status 状态码
  /// @param body 响应体
  public static void stubGoogleTokenFailure(int status, String body) {
    SERVER.stubFor(
        post(urlPathEqualTo("/google/token"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  /// 让 GitHub 的令牌端点回指定的状态和响应体。真实的 GitHub 拒绝授权码时回 200，错误写在 JSON 里。
  ///
  /// @param status 状态码
  /// @param contentType 响应类型
  /// @param body 响应体
  public static void stubGithubTokenResponse(int status, String contentType, String body) {
    SERVER.stubFor(
        post(urlPathEqualTo("/github/token"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", contentType)
                    .withBody(body)));
  }

  /// 让 GitHub 的令牌端点断开连接：模拟真正的网络错误。
  public static void stubGithubTokenConnectionFailure() {
    SERVER.stubFor(
        post(urlPathEqualTo("/github/token"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
  }

  /// 让 GitHub 的令牌和资料端点回一个账号。
  ///
  /// @param id 数字 ID
  /// @param login 登录名
  /// @param email 公开邮箱，可以为 `null`
  public static void stubGithub(long id, String login, String email) {
    SERVER.stubFor(
        post(urlPathEqualTo("/github/token"))
            .willReturn(
                okJson(
                    "{\"access_token\":\""
                        + ACCESS_TOKEN
                        + "\",\"token_type\":\"bearer\",\"scope\":\"read:user\"}")));
    String emailJson = email == null ? "null" : "\"" + email + "\"";
    SERVER.stubFor(
        get(urlPathEqualTo("/github/user"))
            .willReturn(
                okJson(
                    "{\"id\":"
                        + id
                        + ",\"login\":\""
                        + login
                        + "\",\"email\":"
                        + emailJson
                        + "}")));
  }

  /// 正常的 `id_token` 声明，协议攻击用例在此基础上改。
  ///
  /// @param issuer 签发方
  /// @param audience 受众
  /// @param subject 主体
  /// @param nonce `nonce`
  /// @return 声明构建器
  public static JWTClaimsSet.Builder claims(
      String issuer, String audience, String subject, String nonce) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .subject(subject)
        .claim("nonce", nonce)
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))));
  }

  /// 用平台的私钥签。
  ///
  /// @param claims 声明
  /// @return `id_token`
  public static String signedByProvider(JWTClaimsSet claims) {
    return idToken(SIGNING_KEY, claims);
  }

  /// 用公钥集合里没有的私钥签（`kid` 相同），模拟伪造。
  ///
  /// @param claims 声明
  /// @return `id_token`
  public static String signedByForeignKey(JWTClaimsSet claims) {
    return idToken(FOREIGN_KEY, claims);
  }

  private static void stubToken(String path, String idToken) {
    String body =
        "{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"id_token\":\"%s\"}"
            .formatted(ACCESS_TOKEN, idToken);
    SERVER.stubFor(post(urlPathEqualTo(path)).willReturn(okJson(body)));
  }

  private static String idToken(RSAKey key, JWTClaimsSet claims) {
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generateKey(String keyId) {
    try {
      return new RSAKeyGenerator(2048).keyID(keyId).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
