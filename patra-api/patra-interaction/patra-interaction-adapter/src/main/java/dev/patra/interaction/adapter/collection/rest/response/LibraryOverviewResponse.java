package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.LibraryOverviewView;
import java.util.List;

/// 文献库概览：「全部」「未分类」两个计数与全部收藏夹（按更新时间倒序）。
///
/// @param total 全部
/// @param unfiled 未分类
/// @param collections 收藏夹
public record LibraryOverviewResponse(
    int total, int unfiled, List<CollectionResponse> collections) {

  /// 防御性拷贝。
  public LibraryOverviewResponse {
    collections = List.copyOf(collections);
  }

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static LibraryOverviewResponse from(LibraryOverviewView view) {
    return new LibraryOverviewResponse(
        view.total(),
        view.unfiled(),
        view.collections().stream().map(CollectionResponse::from).toList());
  }
}
