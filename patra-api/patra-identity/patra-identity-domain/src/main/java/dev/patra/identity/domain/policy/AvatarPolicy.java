package dev.patra.identity.domain.policy;

/// 头像的产品规则（release spec 2.1.3）。原图 5 MB 上限由 multipart 配置守，不在这里。
public final class AvatarPolicy {

  /// 短边至少几像素。
  public static final int MIN_SHORT_SIDE = 128;

  /// 宽 × 高最多多少像素：手机直出最大约 4800 万，挡住 5 MB 内声明出超大尺寸的文件。
  public static final long MAX_PIXELS = 50_000_000L;

  /// WebP 有损编码质量。
  public static final float WEBP_QUALITY = 0.82f;

  private AvatarPolicy() {}
}
