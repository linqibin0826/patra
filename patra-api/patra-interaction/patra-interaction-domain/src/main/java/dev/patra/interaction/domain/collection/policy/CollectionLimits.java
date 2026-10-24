package dev.patra.interaction.domain.collection.policy;

/// 文献库与收藏夹的数量上限（release spec 2.1.1、2.1.2）。
public final class CollectionLimits {

  /// 每人文献库条目上限。
  public static final int LIBRARY_MAX_ITEMS = 20_000;

  /// 每人收藏夹个数上限。
  public static final int MAX_COLLECTIONS = 200;

  /// 每个收藏夹的条目上限。
  public static final int COLLECTION_MAX_ITEMS = 5_000;

  /// 一次批量操作（条目 ID、「我的状态」的 key）的上限。
  public static final int MAX_BATCH = 100;

  /// 一次导出的 key 上限（与 catalog 导出接口一致）。
  public static final int MAX_EXPORT = 5_000;

  /// 只有常量。
  private CollectionLimits() {}
}
