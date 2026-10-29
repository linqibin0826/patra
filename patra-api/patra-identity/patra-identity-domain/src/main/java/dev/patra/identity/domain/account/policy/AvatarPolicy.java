package dev.patra.identity.domain.account.policy;

/// 头像的产品规则（release spec 2.1.3）。原图 5 MB 上限由 multipart 配置守，不在这里。
public final class AvatarPolicy {

  /// 短边至少几像素。
  public static final int MIN_SHORT_SIDE = 128;

  /// 宽 × 高最多多少像素（4096²）：WebP 与渐进式 JPEG 解码时整张图进内存（49 MP 的 WebP 堆峰值约 300 MB），
  /// 不能靠下采样封顶；门户上传的是裁好的方图（边长 ≤ 1024），这个上限只挡直接调接口的超大图。
  public static final long MAX_PIXELS = 4096L * 4096L;

  /// WebP 有损编码质量。
  public static final float WEBP_QUALITY = 0.82f;

  private AvatarPolicy() {}
}
