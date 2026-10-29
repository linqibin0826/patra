package dev.patra.identity.infra.account.avatar;

import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.decodeWebp;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.gray;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.grayWithAlpha;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.jpeg;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.png;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.pngHeaderOnly;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.riffChunks;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.webp;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.withExifGps;
import static dev.patra.identity.infra.account.avatar.AvatarImageFixture.withPngMetadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvatarImageAdapterTest {

  private final AvatarImageAdapter adapter = new AvatarImageAdapter();

  @Test
  void should_render_both_sizes_as_webp_from_jpeg() {
    assertBothSizes(adapter.render(jpeg(600, 600)));
  }

  @Test
  void should_render_both_sizes_as_webp_from_png() {
    assertBothSizes(adapter.render(png(600, 600, false)));
  }

  @Test
  void should_render_both_sizes_as_webp_from_webp() {
    assertBothSizes(adapter.render(webp(600, 600)));
  }

  @Test
  void should_center_crop_landscape_image() {
    BufferedImage large = decodeWebp(adapter.render(jpeg(1600, 800)).large());

    assertGreen(large.getRGB(0, 0));
    assertGreen(large.getRGB(511, 0));
    assertGreen(large.getRGB(511, 511));
  }

  @Test
  void should_downscale_large_photo() {
    BufferedImage large = decodeWebp(adapter.render(jpeg(4000, 3000)).large());

    assertThat(large.getWidth()).isEqualTo(512);
    assertGreen(large.getRGB(256, 256));
  }

  @Test
  void should_keep_alpha_for_transparent_png() {
    BufferedImage large = decodeWebp(adapter.render(png(400, 400, true)).large());

    assertThat(large.getRGB(0, 0) >>> 24).isLessThan(16);
    assertThat(large.getRGB(256, 256) >>> 24).isGreaterThan(240);
  }

  @Test
  void should_upscale_small_square_to_512() {
    assertBothSizes(adapter.render(png(200, 200, false)));
  }

  @Test
  void should_accept_short_side_128() {
    assertBothSizes(adapter.render(png(128, 300, false)));
  }

  @Test
  void should_reject_short_side_127_as_too_small() {
    assertRejected(() -> adapter.render(png(127, 300, false)), AvatarRejection.TOO_SMALL);
  }

  @Test
  void should_reject_too_many_pixels_without_decoding() {
    assertRejected(
        () -> adapter.render(pngHeaderOnly(8000, 8000)), AvatarRejection.TOO_MANY_PIXELS);
  }

  /// 像素上限 4096²：WebP 与渐进式 JPEG 解码时不受下采样约束，49 MP 的 WebP 堆峰值约 300 MB。
  @Test
  void should_reject_more_than_4096_squared_pixels() {
    assertRejected(
        () -> adapter.render(pngHeaderOnly(4097, 4096)), AvatarRejection.TOO_MANY_PIXELS);
  }

  /// 同时出图的路数有上限，等不到名额回 503，不让并发上传把堆占满。
  @Test
  void should_answer_unavailable_when_render_slots_are_busy() {
    AvatarImageAdapter busy = new AvatarImageAdapter(new Semaphore(0), Duration.ofMillis(10));

    assertThatThrownBy(() -> busy.render(png(300, 300, false)))
        .isInstanceOf(TemporarilyUnavailableException.class);
  }

  @Test
  void should_reject_truncated_jpeg_as_unreadable() {
    byte[] whole = jpeg(600, 600);

    assertRejected(
        () -> adapter.render(Arrays.copyOf(whole, whole.length / 2)), AvatarRejection.UNREADABLE);
  }

  @Test
  void should_reject_header_only_png_as_unreadable() {
    assertRejected(() -> adapter.render(pngHeaderOnly(600, 600)), AvatarRejection.UNREADABLE);
  }

  @Test
  void should_reject_text_named_jpg() {
    assertRejected(
        () -> adapter.render("not an image".getBytes(StandardCharsets.UTF_8)),
        AvatarRejection.UNSUPPORTED_TYPE);
  }

  @Test
  void should_strip_exif_xmp_and_icc() {
    for (byte[] upload :
        new byte[][] {withExifGps(jpeg(600, 600)), withPngMetadata(png(600, 600, false))}) {
      AvatarRenditions renditions = adapter.render(upload);

      for (byte[] output : new byte[][] {renditions.large(), renditions.small()}) {
        assertThat(riffChunks(output)).doesNotContain("EXIF", "XMP ", "ICCP");
      }
    }
  }

  /// 灰度图按 sRGB 编码的灰度值原样变成 r = g = b，不能被当成线性灰度提亮（中灰 128 不能变成 189）。
  @ParameterizedTest
  @ValueSource(strings = {"png", "png-alpha", "jpg"})
  void should_keep_gray_level_of_grayscale_images(String format) {
    BufferedImage large = decodeWebp(adapter.render(gray(300, 300, 128, format)).large());

    int argb = large.getRGB(256, 256);
    assertThat((argb >> 16) & 0xFF).isBetween(118, 138);
    assertThat((argb >> 8) & 0xFF).isBetween(118, 138);
    assertThat(argb & 0xFF).isBetween(118, 138);
  }

  /// 半透明灰度图的灰度值不能被预乘两次而变暗：灰度 200、alpha 128 出图后仍约为 200。
  @Test
  void should_keep_gray_level_of_semi_transparent_grayscale_images() {
    BufferedImage large = decodeWebp(adapter.render(grayWithAlpha(300, 300, 200, 128)).large());

    int argb = large.getRGB(256, 256);
    assertThat((argb >>> 24) & 0xFF).isBetween(118, 138);
    assertThat((argb >> 16) & 0xFF).isBetween(190, 210);
    assertThat((argb >> 8) & 0xFF).isBetween(190, 210);
    assertThat(argb & 0xFF).isBetween(190, 210);
  }

  /// 两档都是 WebP，尺寸分别 512 与 128。
  ///
  /// @param renditions 出图结果
  private static void assertBothSizes(AvatarRenditions renditions) {
    for (byte[] bytes : new byte[][] {renditions.large(), renditions.small()}) {
      assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
      assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
    }
    BufferedImage large = decodeWebp(renditions.large());
    BufferedImage small = decodeWebp(renditions.small());
    assertThat(large.getWidth()).isEqualTo(512);
    assertThat(large.getHeight()).isEqualTo(512);
    assertThat(small.getWidth()).isEqualTo(128);
    assertThat(small.getHeight()).isEqualTo(128);
  }

  /// 像素是绿色：绿通道远大于红蓝（有损编码有色偏，留余量）。
  ///
  /// @param argb 像素
  private static void assertGreen(int argb) {
    int red = (argb >> 16) & 0xFF;
    int green = (argb >> 8) & 0xFF;
    int blue = argb & 0xFF;
    assertThat(green).isGreaterThan(200);
    assertThat(red).isLessThan(40);
    assertThat(blue).isLessThan(40);
  }

  /// 断言被拒且原因相符。
  ///
  /// @param call 调用
  /// @param rejection 期望原因
  private static void assertRejected(ThrowingCallable call, AvatarRejection rejection) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidAvatarException.class)
        .extracting(e -> ((InvalidAvatarException) e).getRejection())
        .isEqualTo(rejection);
  }
}
