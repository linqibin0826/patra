package dev.patra.catalog.adapter.portal.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/// 导出请求（`POST /portal/publications/export`，spec 第 6.1 节）。
///
/// @param ids 文献 ID，1 ~ 5,000 个正整数（JSON 里字符串或数字都接受）
/// @param format `ris` / `bibtex` / `csv` / `csl-json`
/// @param name 可选，≤ 100 字，用于文件名
/// @author linqibin
/// @since 0.1.0
public record PortalPublicationExportRequest(
    @NotEmpty(message = "ids 至少 1 个") @Size(max = 5000, message = "ids 最多 5000 个")
        List<@Positive(message = "ids 须为正整数") Long> ids,
    @NotBlank(message = "format 不能为空")
        @Pattern(
            regexp = "ris|bibtex|csv|csl-json",
            message = "format 须为 ris、bibtex、csv、csl-json 之一")
        String format,
    @Size(max = 100, message = "name 最多 100 字") String name) {

  /// 紧凑构造器：缺失的 ids 视为空列表（交给 `@NotEmpty`），防御性拷贝。
  public PortalPublicationExportRequest {
    ids = ids == null ? List.of() : List.copyOf(ids);
  }
}
