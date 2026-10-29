package dev.patra.catalog.adapter.kernel.rest.request;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.text.Normalizer;

/// Portal 建议请求（机构与 MeSH 两个端点共用）。
///
/// 适配器入口校验：`q` 在紧凑构造器里按 [SuggestQuery] 同样的规则归一（NFKC、去首尾空白、折叠空白），
/// 再按码点校验 2 ~ 100；`limit` 1 ~ 20，缺省 [SuggestQuery#DEFAULT_LIMIT]。非法参数收敛为 422。
///
/// @param q 查询文本
/// @param limit 最多返回条数（可空）
/// @author linqibin
/// @since 0.1.0
public record PortalSuggestRequest(
    @NotBlank(message = "q 不能为空") String q,
    @Min(value = 1, message = "limit 最小为 1") @Max(value = 20, message = "limit 最大为 20")
        Integer limit) {

  /// 紧凑构造器：归一化 `q`，`null` 保持 `null` 交给 `@NotBlank` 报。
  public PortalSuggestRequest {
    q = q == null ? null : normalize(q);
  }

  /// 长度自检：按码点算 2 ~ 100；`q` 为空时返回 `true`，由 `@NotBlank` 报缺失。
  ///
  /// @return 长度是否合法
  @AssertTrue(message = "q 须为 2–100 个字符")
  public boolean isQLengthValid() {
    if (q == null) {
      return true;
    }
    int length = q.codePointCount(0, q.length());
    return length >= SuggestQuery.MIN_LENGTH && length <= SuggestQuery.MAX_LENGTH;
  }

  /// `limit` 为空时给缺省值。
  ///
  /// @return 最多返回条数
  public int limitOrDefault() {
    return limit != null ? limit : SuggestQuery.DEFAULT_LIMIT;
  }

  /// NFKC → 去首尾空白 → 连续空白折成一个空格。
  ///
  /// @param raw 原始文本
  /// @return 归一化后的文本
  private static String normalize(String raw) {
    return Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ");
  }
}
