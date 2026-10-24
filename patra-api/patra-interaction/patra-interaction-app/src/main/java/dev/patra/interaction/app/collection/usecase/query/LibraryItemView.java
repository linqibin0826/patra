package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 我的文献库里的一条（含收藏夹视图）。
///
/// @param itemId 条目（= 对象 ID）
/// @param type 对象种类
/// @param key 对外 key；catalog 整行消失时为 `null`
/// @param state 正常 / 已失效
/// @param title 已失效时收藏时记下的标题，正常时为 `null`
/// @param note 备注
/// @param collectedAt 收藏时间
/// @param addedAt 加入当前收藏夹的时间，只在收藏夹视图里有
/// @param collectionIds 所在的收藏夹
@Builder
public record LibraryItemView(
    SubjectId itemId,
    SubjectType type,
    String key,
    SubjectState state,
    String title,
    String note,
    Instant collectedAt,
    Instant addedAt,
    List<CollectionId> collectionIds) {

  /// 防御性拷贝。
  public LibraryItemView {
    collectionIds = collectionIds != null ? List.copyOf(collectionIds) : List.of();
  }
}
