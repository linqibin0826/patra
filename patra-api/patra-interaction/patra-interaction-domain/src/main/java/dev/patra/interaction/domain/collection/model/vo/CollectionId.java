package dev.patra.interaction.domain.collection.model.vo;

/// 收藏夹 ID（Snowflake）。
///
/// @param value ID
public record CollectionId(long value) {

  /// 创建。
  ///
  /// @param value ID
  /// @return 收藏夹 ID
  public static CollectionId of(long value) {
    return new CollectionId(value);
  }
}
