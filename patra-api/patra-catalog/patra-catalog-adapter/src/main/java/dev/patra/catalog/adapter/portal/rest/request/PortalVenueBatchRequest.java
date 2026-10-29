package dev.patra.catalog.adapter.portal.rest.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/// Portal 批量期刊卡片请求（`GET /portal/venues/batch?id=…&id=…`）。
///
/// @param id 期刊 ID 列表（重复参数），1 ~ 100 个正整数
/// @author linqibin
/// @since 0.1.0
public record PortalVenueBatchRequest(
    @NotEmpty(message = "id 至少 1 个") @Size(max = 100, message = "id 最多 100 个")
        List<@Positive(message = "id 须为正整数") Long> id) {

  /// 紧凑构造器：丢弃 null 元素后防御性拷贝（`?id=` 空值转换为 null，空列表交给 `@NotEmpty`）。
  public PortalVenueBatchRequest {
    id = id == null ? List.of() : List.copyOf(id.stream().filter(Objects::nonNull).toList());
  }
}
