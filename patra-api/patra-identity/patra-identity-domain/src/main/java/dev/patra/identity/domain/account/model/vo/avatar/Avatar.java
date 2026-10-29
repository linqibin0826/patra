package dev.patra.identity.domain.account.model.vo.avatar;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 用户上传的头像：用户 ID 加内容哈希。哈希是 512 档 WebP 字节 SHA-256 的前 16 位小写十六进制，
/// 两档共用；换头像就换哈希，对象键与读取地址都跟着变，所以响应可以按不可变长期缓存。
///
/// 对象键 `avatar/{userId}/{hash}-{512|128}.webp`；资料里只存 512 档的键。
///
/// @param userId 用户 ID
/// @param hash 内容哈希
public record Avatar(long userId, String hash) {

  private static final Pattern HASH = Pattern.compile("[0-9a-f]{16}");

  private static final Pattern LARGE_KEY =
      Pattern.compile("avatar/(\\d+)/([0-9a-f]{16})-512\\.webp");

  /// 校验哈希格式。
  ///
  /// @throws IllegalArgumentException 哈希不是 16 位小写十六进制
  public Avatar {
    if (hash == null || !HASH.matcher(hash).matches()) {
      throw new IllegalArgumentException("头像哈希必须是 16 位小写十六进制: " + hash);
    }
  }

  /// 工厂方法。
  ///
  /// @param userId 用户 ID
  /// @param hash 内容哈希
  /// @return 头像
  public static Avatar of(long userId, String hash) {
    return new Avatar(userId, hash);
  }

  /// 按 512 档的 WebP 字节算出头像。
  ///
  /// @param userId 用户 ID
  /// @param largeWebp 512 档的 WebP 字节
  /// @return 头像
  public static Avatar forContent(long userId, byte[] largeWebp) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(largeWebp);
      return new Avatar(userId, HexFormat.of().formatHex(digest).substring(0, 16));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }

  /// 从资料里存的 512 档对象键还原。
  ///
  /// @param userId 资料所属用户的 ID
  /// @param largeObjectKey 512 档对象键
  /// @return 头像
  /// @throws IllegalArgumentException 键格式不对或前缀里的用户 ID 不是 `userId`
  public static Avatar parse(long userId, String largeObjectKey) {
    Matcher matcher = LARGE_KEY.matcher(largeObjectKey == null ? "" : largeObjectKey);
    if (!matcher.matches() || !String.valueOf(userId).equals(matcher.group(1))) {
      throw new IllegalArgumentException("不是用户 " + userId + " 的头像对象键: " + largeObjectKey);
    }
    return new Avatar(userId, matcher.group(2));
  }

  /// 某个用户全部头像对象的键前缀，给注销时按前缀删除。
  ///
  /// @param userId 用户 ID
  /// @return `avatar/{userId}/`
  public static String prefix(long userId) {
    return "avatar/" + userId + "/";
  }

  /// 某一档的对象键。
  ///
  /// @param size 尺寸
  /// @return 对象键
  public String objectKey(AvatarSize size) {
    return prefix(userId) + hash + "-" + size.getPixels() + ".webp";
  }

  /// 某一档的强 ETag，带引号。
  ///
  /// @param size 尺寸
  /// @return ETag
  public String etag(AvatarSize size) {
    return "\"" + hash + "-" + size.getPixels() + "\"";
  }
}
