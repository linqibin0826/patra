package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import java.util.List;

/// 文献库概览：两个计数与全部收藏夹（侧栏与收藏浮层用）。
///
/// @param total 全部
/// @param unfiled 未分类
/// @param collections 收藏夹，按更新时间倒序
public record LibraryOverviewView(int total, int unfiled, List<CollectionSummary> collections) {

  /// 防御性拷贝。
  public LibraryOverviewView {
    collections = List.copyOf(collections);
  }

  /// 创建。
  ///
  /// @param total 全部
  /// @param unfiled 未分类
  /// @param collections 收藏夹
  /// @return 概览
  public static LibraryOverviewView of(
      int total, int unfiled, List<CollectionSummary> collections) {
    return new LibraryOverviewView(total, unfiled, collections);
  }
}
