package dev.patra.interaction.domain.discussion.policy;

import java.time.Duration;

/// 讨论的数量与时间规则（spec 2.1.1、设计简报附录 A「PAP-121」）。
public final class DiscussionLimits {

  /// 正文最多多少个扩展字素簇。
  public static final int MAX_GRAPHEMES = 5_000;

  /// 正文最多多少 UTF-8 字节（字素上限 × 4，防止一个字素里塞几百个组合码点）。
  public static final int MAX_BYTES = 20_000;

  /// 一条评论最多 @ 几个人（按解析出的不同用户 ID 计）。
  public static final int MAX_MENTIONS = 10;

  /// 一条评论最多有多少个 @ 候选（按原文去重）：identity 解析一次最多 100 个。
  public static final int MAX_MENTION_CANDIDATES = 100;

  /// 讨论列表每页默认几楼。
  public static final int THREAD_PAGE_DEFAULT = 20;

  /// 分页参数 `size` 的上限（讨论列表与楼内回复）。
  public static final int PAGE_MAX = 50;

  /// 列表里每楼先给几条回复。
  public static final int PREVIEW_REPLIES = 3;

  /// 楼内回复每页默认几条。
  public static final int REPLY_PAGE_DEFAULT = 50;

  /// 发布后多久之内的修改不记版本（含整 5 分钟）。
  public static final Duration EDIT_GRACE = Duration.ofMinutes(5);

  /// @ 联想最多列几位本讨论的参与者。
  public static final int SUGGEST_PARTICIPANTS = 6;

  /// @ 联想合计最多几位。
  public static final int SUGGEST_TOTAL = 8;

  /// @ 联想从最近发言的多少位参与者里挑。
  public static final int PARTICIPANT_SCAN = 50;

  /// identity 前缀联想一次最多要几位。
  public static final int IDENTITY_SUGGEST_MAX = 20;

  /// 只有常量。
  private DiscussionLimits() {}
}
