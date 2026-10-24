package dev.patra.interaction.domain.collection.model.read;

/// 注销清理删掉的一个用户的行数。
///
/// @param libraryItems 文献库条目
/// @param collections 收藏夹
/// @param collectionItems 收藏夹条目
public record DeletedUserData(int libraryItems, int collections, int collectionItems) {

  /// 创建。
  ///
  /// @param libraryItems 文献库条目
  /// @param collections 收藏夹
  /// @param collectionItems 收藏夹条目
  /// @return 行数
  public static DeletedUserData of(int libraryItems, int collections, int collectionItems) {
    return new DeletedUserData(libraryItems, collections, collectionItems);
  }
}
