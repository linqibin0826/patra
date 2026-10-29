package dev.patra.catalog.api.publication.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 按内部 ID 批量解析文献的请求。
///
/// @param ids 文献主键，1 ~ 100 个正整数
/// @author linqibin
/// @since 0.1.0
public record PublicationResolveByIdsReq(
    @NotEmpty(message = "ids 不能为空") @Size(max = 100, message = "ids 最多 100 个")
        List<@Positive(message = "id 须为正整数") Long> ids) {

  /// 紧凑构造器：防御性拷贝；`null` 原样保留交给 `@NotEmpty`（含 `null` 元素时拷贝失败，按消息不可读处理）。
  public PublicationResolveByIdsReq {
    ids = ids != null ? List.copyOf(ids) : null;
  }

  /// 创建请求。
  ///
  /// @param ids 文献主键
  /// @return 请求
  public static PublicationResolveByIdsReq of(List<Long> ids) {
    return new PublicationResolveByIdsReq(ids);
  }
}
