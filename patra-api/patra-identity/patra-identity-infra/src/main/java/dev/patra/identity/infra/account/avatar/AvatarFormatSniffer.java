package dev.patra.identity.infra.account.avatar;

import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import java.nio.charset.StandardCharsets;

/// 按文件头的魔数判断头像格式，扩展名与 `Content-Type` 一概不看；动图（APNG、动画 WebP）与 GIF 拒收。
///
/// 只走块头、不解码：PNG 在第一个 `IDAT` 之前出现 `acTL` 即 APNG；WebP 的 `VP8X` 标志位动画位为 1，
/// 或出现 `ANIM` / `ANMF` 块即动画。块越过文件末尾时停止扫描，留给解码去报「读不出」。
public final class AvatarFormatSniffer {

  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  private static final int WEBP_ANIMATION_FLAG = 0x02;

  private AvatarFormatSniffer() {}

  /// 判断格式。
  ///
  /// @param bytes 文件字节
  /// @return 格式
  /// @throws InvalidAvatarException 不是 JPEG / PNG / WebP，或是动图（`UNSUPPORTED_TYPE`）
  public static AvatarFormat sniff(byte[] bytes) {
    if (startsWith(bytes, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
      return AvatarFormat.JPEG;
    }
    if (startsWith(bytes, 0, PNG_SIGNATURE)) {
      requireStillPng(bytes);
      return AvatarFormat.PNG;
    }
    if (bytes.length >= 12 && ascii(bytes, 0).equals("RIFF") && ascii(bytes, 8).equals("WEBP")) {
      requireStillWebp(bytes);
      return AvatarFormat.WEBP;
    }
    throw unsupported();
  }

  /// PNG 的块：4 字节大端长度 + 4 字节类型 + 数据 + 4 字节 CRC。
  ///
  /// @param bytes 文件字节
  private static void requireStillPng(byte[] bytes) {
    long offset = PNG_SIGNATURE.length;
    while (offset + 8 <= bytes.length) {
      int at = (int) offset;
      long length = readIntBigEndian(bytes, at) & 0xFFFFFFFFL;
      String type = ascii(bytes, at + 4);
      if (type.equals("IDAT")) {
        return;
      }
      if (type.equals("acTL")) {
        throw unsupported();
      }
      offset += 12 + length;
    }
  }

  /// WebP 的块：4 字节类型 + 4 字节小端长度 + 数据，奇数长度补 1 字节。
  ///
  /// @param bytes 文件字节
  private static void requireStillWebp(byte[] bytes) {
    long offset = 12;
    while (offset + 8 <= bytes.length) {
      int at = (int) offset;
      String type = ascii(bytes, at);
      long length = readIntLittleEndian(bytes, at + 4) & 0xFFFFFFFFL;
      if (type.equals("ANIM") || type.equals("ANMF")) {
        throw unsupported();
      }
      if (type.equals("VP8X")
          && at + 8 < bytes.length
          && (bytes[at + 8] & WEBP_ANIMATION_FLAG) != 0) {
        throw unsupported();
      }
      offset += 8 + length + (length & 1);
    }
  }

  /// 是否以给定字节开头。
  ///
  /// @param bytes 文件字节
  /// @param offset 起点
  /// @param prefix 前缀
  /// @return 匹配时为 `true`
  private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
    if (bytes.length < offset + prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  /// 读 4 个 ASCII 字符。
  ///
  /// @param bytes 文件字节
  /// @param offset 起点
  /// @return 字符串
  private static String ascii(byte[] bytes, int offset) {
    return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
  }

  /// 读大端 32 位整数。
  ///
  /// @param bytes 文件字节
  /// @param offset 起点
  /// @return 整数
  private static int readIntBigEndian(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) << 24
        | (bytes[offset + 1] & 0xFF) << 16
        | (bytes[offset + 2] & 0xFF) << 8
        | (bytes[offset + 3] & 0xFF);
  }

  /// 读小端 32 位整数。
  ///
  /// @param bytes 文件字节
  /// @param offset 起点
  /// @return 整数
  private static int readIntLittleEndian(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF)
        | (bytes[offset + 1] & 0xFF) << 8
        | (bytes[offset + 2] & 0xFF) << 16
        | (bytes[offset + 3] & 0xFF) << 24;
  }

  /// 类型不对。
  ///
  /// @return 异常
  private static InvalidAvatarException unsupported() {
    return new InvalidAvatarException(AvatarRejection.UNSUPPORTED_TYPE);
  }
}
