package dev.patra.catalog.api.publication.dto;

import java.util.List;

/// 同主题邻居：先同主要主题词（多个词轮流）、再同期刊，按展示顺序。
///
/// @param items 邻居
/// @author linqibin
/// @since 0.1.0
public record SubjectNeighborsResp(List<SubjectNeighborResp> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public SubjectNeighborsResp {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应。
  ///
  /// @param items 邻居
  /// @return 响应
  public static SubjectNeighborsResp of(List<SubjectNeighborResp> items) {
    return new SubjectNeighborsResp(items);
  }
}
