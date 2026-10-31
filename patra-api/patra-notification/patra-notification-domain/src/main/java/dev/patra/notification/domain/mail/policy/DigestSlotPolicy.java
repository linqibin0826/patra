package dev.patra.notification.domain.mail.policy;

import dev.patra.notification.domain.mail.model.vo.DigestSlots;

/// 每日汇总一封最多列 {@value #MAX_ITEMS} 条，两节怎么分（spec 5.2）：两节都有时，回复先拿
/// `ceil(10 × 回复总数 ÷ 两类总数)` 条，但至少给 @ 留 1 条；@ 拿剩下的；哪一节能列的不够名额，余下的给另一节。
public final class DigestSlotPolicy {

  /// 一封最多列几条（单条邮件以外的列表都按它）。
  public static final int MAX_ITEMS = 10;

  /// 只有静态方法，不实例化。
  private DigestSlotPolicy() {}

  /// 分名额。
  ///
  /// @param replyTotal 窗口里回复的未读总数
  /// @param replyListable 回复能列出的条数（取出的、有落点的）
  /// @param mentionTotal 窗口里 @ 的未读总数
  /// @param mentionListable @ 能列出的条数
  /// @return 两节各列几条
  public static DigestSlots allocate(
      int replyTotal, int replyListable, int mentionTotal, int mentionListable) {
    if (replyListable == 0 || mentionListable == 0) {
      return DigestSlots.of(
          Math.min(MAX_ITEMS, replyListable), Math.min(MAX_ITEMS, mentionListable));
    }
    int replyShare =
        Math.min(
            (int) Math.ceil((double) MAX_ITEMS * replyTotal / (replyTotal + mentionTotal)),
            MAX_ITEMS - 1);
    int replies = Math.min(replyShare, replyListable);
    int mentions = Math.min(MAX_ITEMS - replies, mentionListable);
    replies = Math.min(MAX_ITEMS - mentions, replyListable);
    return DigestSlots.of(replies, mentions);
  }
}
