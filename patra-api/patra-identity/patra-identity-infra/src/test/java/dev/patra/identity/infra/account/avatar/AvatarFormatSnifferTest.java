package dev.patra.identity.infra.account.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class AvatarFormatSnifferTest {

  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  @Test
  void should_recognize_jpeg() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    assertThat(AvatarFormatSniffer.sniff(jpeg)).isEqualTo(AvatarFormat.JPEG);
  }

  @Test
  void should_recognize_png() {
    assertThat(AvatarFormatSniffer.sniff(png("IHDR", "IDAT", "IEND"))).isEqualTo(AvatarFormat.PNG);
  }

  @Test
  void should_recognize_simple_and_extended_webp() {
    assertThat(AvatarFormatSniffer.sniff(webp(chunk("VP8 ", new byte[10]))))
        .isEqualTo(AvatarFormat.WEBP);
    assertThat(AvatarFormatSniffer.sniff(webp(vp8x(0x00), chunk("VP8 ", new byte[10]))))
        .isEqualTo(AvatarFormat.WEBP);
  }

  @Test
  void should_reject_gif() {
    assertUnsupported(
        () -> AvatarFormatSniffer.sniff("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void should_reject_apng() {
    assertUnsupported(() -> AvatarFormatSniffer.sniff(png("IHDR", "acTL", "IDAT", "IEND")));
  }

  @Test
  void should_accept_png_with_actl_after_idat_as_still_image() {
    assertThat(AvatarFormatSniffer.sniff(png("IHDR", "IDAT", "acTL", "IEND")))
        .isEqualTo(AvatarFormat.PNG);
  }

  @Test
  void should_reject_animated_webp_by_flag() {
    assertUnsupported(
        () -> AvatarFormatSniffer.sniff(webp(vp8x(0x02), chunk("VP8 ", new byte[10]))));
  }

  @Test
  void should_reject_animated_webp_by_anim_chunk() {
    assertUnsupported(
        () ->
            AvatarFormatSniffer.sniff(
                webp(vp8x(0x00), chunk("ANIM", new byte[6]), chunk("ANMF", new byte[20]))));
  }

  @Test
  void should_reject_text_and_empty_and_truncated() {
    assertUnsupported(() -> AvatarFormatSniffer.sniff("hello".getBytes(StandardCharsets.US_ASCII)));
    assertUnsupported(() -> AvatarFormatSniffer.sniff(new byte[0]));
    assertUnsupported(() -> AvatarFormatSniffer.sniff(new byte[] {(byte) 0x89, 0x50, 0x4E}));
  }

  @Test
  void should_stop_scanning_when_chunk_length_overflows() {
    byte[] png = png("IHDR", "IDAT");
    // IHDR 的长度改成 0xFFFFFFF0：下一个块头落在文件之外，不能越界也不能死循环
    png[8] = (byte) 0xFF;
    png[9] = (byte) 0xFF;
    png[10] = (byte) 0xFF;
    png[11] = (byte) 0xF0;
    byte[] webp = webp(chunk("VP8 ", new byte[10]));
    webp[16] = (byte) 0xF0;
    webp[17] = (byte) 0xFF;
    webp[18] = (byte) 0xFF;
    webp[19] = (byte) 0x7F;

    assertThat(AvatarFormatSniffer.sniff(png)).isEqualTo(AvatarFormat.PNG);
    assertThat(AvatarFormatSniffer.sniff(webp)).isEqualTo(AvatarFormat.WEBP);
  }

  /// 断言被拒且原因是类型不对。
  ///
  /// @param call 调用
  private static void assertUnsupported(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidAvatarException.class)
        .extracting(e -> ((InvalidAvatarException) e).getRejection())
        .isEqualTo(AvatarRejection.UNSUPPORTED_TYPE);
  }

  /// 拼一个 PNG：签名 + 按顺序的空块（数据长度 0，CRC 填零，嗅探不校验 CRC）。
  ///
  /// @param types 块类型
  /// @return 字节
  private static byte[] png(String... types) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(PNG_SIGNATURE);
    for (String type : types) {
      byte[] data = "IHDR".equals(type) ? new byte[13] : new byte[0];
      out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
      out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
      out.writeBytes(data);
      out.writeBytes(new byte[4]);
    }
    return out.toByteArray();
  }

  /// 拼一个 WebP：`RIFF` + 长度 + `WEBP` + 块。
  ///
  /// @param chunks 块
  /// @return 字节
  private static byte[] webp(byte[]... chunks) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
    for (byte[] chunk : chunks) {
      body.writeBytes(chunk);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array());
    out.writeBytes(body.toByteArray());
    return out.toByteArray();
  }

  /// 一个 `VP8X` 块，首字节是标志位。
  ///
  /// @param flags 标志位
  /// @return 块
  private static byte[] vp8x(int flags) {
    byte[] data = new byte[10];
    data[0] = (byte) flags;
    return chunk("VP8X", data);
  }

  /// 一个 RIFF 块：类型 + 小端长度 + 数据（奇数长度补 1 字节）。
  ///
  /// @param type 类型
  /// @param data 数据
  /// @return 块
  private static byte[] chunk(String type, byte[] data) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
    out.writeBytes(
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
    out.writeBytes(data);
    if (data.length % 2 == 1) {
      out.write(0);
    }
    return out.toByteArray();
  }
}
