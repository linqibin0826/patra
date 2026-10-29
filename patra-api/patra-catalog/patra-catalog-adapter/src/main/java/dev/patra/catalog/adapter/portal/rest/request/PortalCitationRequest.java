package dev.patra.catalog.adapter.portal.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 复制引用请求（`POST /portal/publications/citations`，spec 第 7.1 节）。
///
/// @param ids 文献 ID，1 ~ 100 个正整数
/// @param style `gb-t-7714-2015` / `vancouver` / `apa-7` / `ama-11`
/// @author linqibin
/// @since 0.1.0
public record PortalCitationRequest(
    @NotEmpty(message = "ids 至少 1 个") @Size(max = 100, message = "ids 最多 100 个")
        List<@Positive(message = "ids 须为正整数") Long> ids,
    @NotBlank(message = "style 不能为空")
        @Pattern(
            regexp = "gb-t-7714-2015|vancouver|apa-7|ama-11",
            message = "style 须为 gb-t-7714-2015、vancouver、apa-7、ama-11 之一")
        String style) {

  /// 紧凑构造器：缺失的 ids 视为空列表（交给 `@NotEmpty`），防御性拷贝。
  public PortalCitationRequest {
    ids = ids == null ? List.of() : List.copyOf(ids);
  }
}
