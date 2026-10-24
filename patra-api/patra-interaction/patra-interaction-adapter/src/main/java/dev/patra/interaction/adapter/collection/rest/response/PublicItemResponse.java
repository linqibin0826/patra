package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.PublicItemView;
import java.time.Instant;
import lombok.Builder;

/// 公开收藏夹里的一条：不带所有者的备注与其他归属；访客自己的收藏状态走「我的状态」。
///
/// @param itemId 条目 ID
/// @param type 对象类型
/// @param key catalog 文献 ID，可空
/// @param state `ACTIVE` / `UNAVAILABLE`
/// @param title 已失效时的快照标题
/// @param addedAt 加入该夹的时间
@Builder
public record PublicItemResponse(
    Long itemId, String type, String key, String state, String title, Instant addedAt) {

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static PublicItemResponse from(PublicItemView view) {
    return PublicItemResponse.builder()
        .itemId(view.itemId().value())
        .type(view.type().slug())
        .key(view.key())
        .state(view.state().name())
        .title(view.title())
        .addedAt(view.addedAt())
        .build();
  }
}
