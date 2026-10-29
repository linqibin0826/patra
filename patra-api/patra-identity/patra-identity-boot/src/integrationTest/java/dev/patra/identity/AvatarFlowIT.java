package dev.patra.identity;

import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.decodeWebp;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.jpeg;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.png;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.riffChunks;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.withExifGps;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.domain.DownloadResult;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/// 头像的完整流程（PAP-97）：真实的过滤器链、PostgreSQL、Redis、MinIO；上传后直接从 MinIO 读回对象校验
/// 格式、尺寸与元数据已剥除。
@DisplayName("头像的完整流程")
class AvatarFlowIT extends IdentityFlowITSupport {

  private static final String BUCKET = "patra-identity-avatar";
  private static final int FIVE_MB = 5 * 1024 * 1024;

  @Autowired ObjectStorageOperations storage;

  @Test
  @DisplayName("上传、读取、重复上传、换图、删除")
  void should_upload_read_replace_and_remove_avatar() {
    long userId = userIdOf(register("flow.avatar@example.com", "u-avatar"));
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    // 上传带 EXIF GPS 的 JPEG：MinIO 里两档都是不带元数据的 WebP
    BinaryResponse uploaded = upload(withExifGps(jpeg(900, 600)), auth);
    assertThat(uploaded.status()).as(uploaded.text()).isEqualTo(200);
    String url = json(uploaded).get("avatarUrl").asString();
    String hash = url.substring(url.indexOf("v=") + 2);
    assertThat(url).isEqualTo("/users/" + userId + "/avatar?v=" + hash);
    byte[] large = object(userId, hash, 512);
    byte[] small = object(userId, hash, 128);
    for (byte[] bytes : new byte[][] {large, small}) {
      assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
      assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
      assertThat(riffChunks(bytes)).doesNotContain("EXIF", "XMP ", "ICCP");
    }
    BufferedImage largeImage = decodeWebp(large);
    assertThat(largeImage.getWidth()).isEqualTo(512);
    assertThat(largeImage.getHeight()).isEqualTo(512);
    assertThat(decodeWebp(small).getWidth()).isEqualTo(128);

    // /auth/me 与本人看自己的主页：同一个地址、同一个色号
    JsonNode me = json(get("/auth/me", auth));
    JsonNode page = json(get("/users/u-avatar", auth));
    assertThat(me.get("avatarUrl").asString()).isEqualTo(url);
    assertThat(page.get("avatarUrl").asString()).isEqualTo(url);
    assertThat(me.get("avatarPalette").asInt()).isBetween(0, 11);
    assertThat(page.get("avatarPalette").asInt()).isEqualTo(me.get("avatarPalette").asInt());

    // 匿名读 128 档：不可变缓存、ETag、image/webp；条件请求 304
    BinaryResponse read = binaryGet(url + "&size=128", noHeaders());
    assertThat(read.status()).isEqualTo(200);
    assertThat(read.headers().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("image/webp");
    assertThat(read.headers().getFirst(HttpHeaders.CACHE_CONTROL))
        .isEqualTo("public, max-age=31536000, immutable");
    assertThat(read.headers().getETag()).isEqualTo("\"" + hash + "-128\"");
    assertThat(read.body()).containsExactly(small);
    HttpHeaders conditional = new HttpHeaders();
    conditional.setIfNoneMatch("\"" + hash + "-128\"");
    assertThat(binaryGet(url + "&size=128", conditional).status()).isEqualTo(304);

    // 改用户名（PAP-108）：/auth/me、主页、批量摘要三处地址不变，原地址照常可读；按用户名的地址 404
    jdbcTemplate.update("UPDATE idn_user SET username = 'u-avatar-renamed' WHERE id = ?", userId);
    assertThat(json(get("/auth/me", auth)).get("avatarUrl").asString()).isEqualTo(url);
    assertThat(json(get("/users/u-avatar-renamed", auth)).get("avatarUrl").asString())
        .isEqualTo(url);
    RawResponse summaries =
        post(
            "/_internal/users/summaries",
            Map.of("userIds", List.of(userId), "viewerUserId", userId),
            noHeaders());
    assertThat(json(summaries).get("users").get(0).get("avatarUrl").asString()).isEqualTo(url);
    assertThat(binaryGet(url + "&size=128", noHeaders()).status()).isEqualTo(200);
    assertThat(binaryGet("/users/u-avatar/avatar?size=128&v=" + hash, noHeaders()).status())
        .isEqualTo(404);
    assertThat(binaryGet("/users/u-avatar-renamed/avatar?size=128&v=" + hash, noHeaders()).status())
        .isEqualTo(404);

    // 同一张图再传一次：地址不变，对象还在
    BinaryResponse again = upload(withExifGps(jpeg(900, 600)), auth);
    assertThat(json(again).get("avatarUrl").asString()).isEqualTo(url);
    assertThat(exists(userId, hash, 512)).isTrue();

    // 换一张：旧对象删掉、资料键换了、旧地址 404
    String newUrl = json(upload(png(400, 400, true), auth)).get("avatarUrl").asString();
    String newHash = newUrl.substring(newUrl.indexOf("v=") + 2);
    assertThat(newHash).isNotEqualTo(hash);
    assertThat(exists(userId, hash, 512)).isFalse();
    assertThat(exists(userId, hash, 128)).isFalse();
    assertThat(avatarKeyOf(userId)).isEqualTo("avatar/" + userId + "/" + newHash + "-512.webp");
    assertThat(binaryGet(url + "&size=512", noHeaders()).status()).isEqualTo(404);
    assertThat(binaryGet(newUrl + "&size=512", noHeaders()).status()).isEqualTo(200);

    // 删除：资料键为空、对象不在、读取 404、/auth/me 回到 null
    BinaryResponse removed = binaryExchange("DELETE", "/me/avatar", auth);
    assertThat(removed.status()).isEqualTo(204);
    assertThat(avatarKeyOf(userId)).isNull();
    assertThat(exists(userId, newHash, 512)).isFalse();
    assertThat(exists(userId, newHash, 128)).isFalse();
    assertThat(binaryGet(newUrl + "&size=512", noHeaders()).status()).isEqualTo(404);
    assertThat(json(get("/auth/me", auth)).get("avatarUrl").isNull()).isTrue();
  }

  @Test
  @DisplayName("超过 5 MB 的上传经真实 HTTP 回 413；正好 5 MB 的非图片回 422")
  void should_reject_upload_over_5mb_with_413() {
    long userId = userIdOf(register("flow.avatar.big@example.com", "u-avatar-big"));
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));
    byte[] tooBig = new byte[FIVE_MB + 1];
    byte[] exact = new byte[FIVE_MB];
    new Random(7).nextBytes(exact);

    BinaryResponse big = upload(tooBig, auth);
    BinaryResponse fits = upload(exact, auth);

    assertThat(big.status()).as(big.text()).isEqualTo(413);
    assertThat(json(big).get("code").asString()).isEqualTo("IDN-0413");
    assertThat(fits.status()).as(fits.text()).isEqualTo(422);
    assertThat(json(fits).get("errors").get(0).get("code").asString())
        .isEqualTo("UNSUPPORTED_TYPE");
  }

  /// 手机直出原图常见 10–20 MB：Tomcat 按 Content-Length 一字节不读就拒，必须把剩下的体读完再回 413，
  /// 否则客户端写到一半撞上连接重置。
  @Test
  @DisplayName("20 MB 的上传经真实 HTTP 也回 413，不是连接重置")
  void should_reject_upload_of_20mb_with_413() {
    long userId = userIdOf(register("flow.avatar.huge@example.com", "u-avatar-huge"));
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    BinaryResponse huge = upload(new byte[20 * 1024 * 1024], auth);

    assertThat(huge.status()).as(huge.text()).isEqualTo(413);
  }

  @Test
  @DisplayName("冷静期账号上传 409")
  void should_reject_upload_during_pending_deletion_with_409() {
    long userId = userIdOf(register("flow.avatar.pending@example.com", "u-avatar-pending"));
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', deletion_due_at = ? WHERE id = ?",
        Timestamp.from(Instant.parse("2026-11-20T00:00:00Z")),
        userId);

    BinaryResponse response = upload(jpeg(300, 300), auth);

    assertThat(response.status()).as(response.text()).isEqualTo(409);
  }

  /// 以 multipart 的 `file` part 上传。
  ///
  /// @param bytes 文件字节
  /// @param auth 断言头
  /// @return 响应
  private BinaryResponse upload(byte[] bytes, HttpHeaders auth) {
    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.IMAGE_JPEG);
    ByteArrayResource resource =
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "avatar.jpg";
          }
        };
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", new HttpEntity<>(resource, partHeaders));
    return rawClient()
        .post()
        .uri("/me/avatar")
        .headers(h -> h.addAll(auth))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .exchange(
            (request, response) ->
                BinaryResponse.read(
                    response.getStatusCode().value(), response.getHeaders(), response.getBody()));
  }

  /// 发 GET，按字节读响应体。
  ///
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  private BinaryResponse binaryGet(String uri, HttpHeaders headers) {
    return binaryExchange("GET", uri, headers);
  }

  /// 发一个无体请求，按字节读响应体。
  ///
  /// @param method 方法
  /// @param uri 路径
  /// @param headers 请求头
  /// @return 响应
  private BinaryResponse binaryExchange(String method, String uri, HttpHeaders headers) {
    return rawClient()
        .method(HttpMethod.valueOf(method))
        .uri(uri)
        .headers(h -> h.addAll(headers))
        .exchange(
            (request, response) ->
                BinaryResponse.read(
                    response.getStatusCode().value(), response.getHeaders(), response.getBody()));
  }

  /// 读 MinIO 里的一档头像。
  ///
  /// @param userId 用户 ID
  /// @param hash 哈希
  /// @param size 尺寸
  /// @return 字节
  private byte[] object(long userId, String hash, int size) {
    try (DownloadResult result = storage.download(BUCKET, key(userId, hash, size))) {
      return result.getContent().readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// MinIO 里有没有这一档。
  ///
  /// @param userId 用户 ID
  /// @param hash 哈希
  /// @param size 尺寸
  /// @return 存在时为 `true`
  private boolean exists(long userId, String hash, int size) {
    return storage.statObject(BUCKET, key(userId, hash, size)).isPresent();
  }

  /// 对象键。
  ///
  /// @param userId 用户 ID
  /// @param hash 哈希
  /// @param size 尺寸
  /// @return 键
  private static String key(long userId, String hash, int size) {
    return "avatar/" + userId + "/" + hash + "-" + size + ".webp";
  }

  /// 资料表里的头像键。
  ///
  /// @param userId 用户 ID
  /// @return 键，没有时为 `null`
  private String avatarKeyOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT avatar_object_key FROM idn_user_profile WHERE user_id = ?", String.class, userId);
  }

  /// 把二进制响应当 JSON 读。
  ///
  /// @param response 响应
  /// @return JSON
  private static JsonNode json(BinaryResponse response) {
    return JSON.readTree(response.text());
  }

  /// 状态、头与字节形式的响应体。
  ///
  /// @param status 状态码
  /// @param headers 响应头
  /// @param body 响应体
  private record BinaryResponse(int status, HttpHeaders headers, byte[] body) {

    /// 读出响应。
    ///
    /// @param status 状态码
    /// @param headers 响应头
    /// @param body 响应体流
    /// @return 响应
    static BinaryResponse read(int status, HttpHeaders headers, InputStream body) {
      try {
        return new BinaryResponse(
            status, HttpHeaders.readOnlyHttpHeaders(headers), body.readAllBytes());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    /// 响应体按 UTF-8 读成文本。
    ///
    /// @return 文本
    String text() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }
}
