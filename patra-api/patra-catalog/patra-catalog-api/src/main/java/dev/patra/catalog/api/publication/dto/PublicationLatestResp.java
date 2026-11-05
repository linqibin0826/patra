package dev.patra.catalog.api.publication.dto;

import java.util.List;

/// 最新收录的可用文献：主键倒序（首次入库时间倒序），去掉软删与撤稿。
///
/// @param ids 文献主键（经 MVC 转换器输出为 JSON 字符串）
/// @author linqibin
/// @since 0.1.0
public record PublicationLatestResp(List<Long> ids) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public PublicationLatestResp {
    ids = ids != null ? List.copyOf(ids) : List.of();
  }

  /// 创建响应。
  ///
  /// @param ids 文献主键
  /// @return 响应
  public static PublicationLatestResp of(List<Long> ids) {
    return new PublicationLatestResp(ids);
  }
}
