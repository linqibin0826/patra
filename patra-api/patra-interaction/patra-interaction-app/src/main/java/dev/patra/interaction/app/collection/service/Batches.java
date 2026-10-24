package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import dev.patra.interaction.domain.collection.policy.CollectionLimits;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.LinkedHashSet;
import java.util.List;

/// 请求参数的公共检查：批量条目 ID、对象类型。
public final class Batches {

  /// 只有静态方法。
  private Batches() {}

  /// 条目 ID（= 对象 ID）：1 ~ 100 个正数，去重保序。
  ///
  /// @param itemIds 请求里的 ID
  /// @return 对象 ID
  /// @throws InvalidCollectionRequestException 空、超限或含非正数
  public static List<SubjectId> requireItemIds(List<Long> itemIds) {
    if (itemIds == null || itemIds.isEmpty() || itemIds.size() > CollectionLimits.MAX_BATCH) {
      throw new InvalidCollectionRequestException("一次 1 ~ " + CollectionLimits.MAX_BATCH + " 个条目");
    }
    LinkedHashSet<SubjectId> ids = new LinkedHashSet<>();
    for (Long id : itemIds) {
      if (id == null || id <= 0) {
        throw new InvalidCollectionRequestException("条目 ID 必须是正整数");
      }
      ids.add(SubjectId.of(id));
    }
    return List.copyOf(ids);
  }

  /// 对象类型。
  ///
  /// @param slug 对外类型名
  /// @return 类型
  /// @throws InvalidCollectionRequestException 未知类型
  public static SubjectType subjectType(String slug) {
    return SubjectType.fromSlug(slug)
        .orElseThrow(() -> new InvalidCollectionRequestException("不支持的对象类型"));
  }
}
