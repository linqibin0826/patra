package dev.linqibin.patra.catalog.adapter.rest.portal.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/// Portal 文献检索 / facets 请求（两个端点共用；facets 忽略 sort / page / pageSize）。
///
/// 适配器入口校验：非法参数在此收敛为 422。多值参数用**重复参数**（`type=A&type=B`），由
/// [RepeatedParamListEditor] 绑定，不按逗号拆分——类型值本身含逗号。
///
/// @param q 标题子串关键词，≤200 字，空白视为不传
/// @param author 作者展示名子串，≤200 字，空白视为不传
/// @param pmid PubMed ID，纯数字 1–15 位
/// @param doi DOI，≤200 字，可带 URL / doi: 前缀
/// @param yearFrom 出版年份下限（1000–9999）
/// @param yearTo 出版年份上限（1000–9999），须 ≥ yearFrom
/// @param venue 期刊 ID 列表，元素为正整数
/// @param type 文献类型完整值列表
/// @param evidence 证据等级名称列表（EvidenceLevel 枚举名，大小写不敏感）
/// @param oa 是否开放获取
/// @param lang 语言基码列表
/// @param sort 排序码：latest / year（大小写不敏感），缺省 latest
/// @param page 页码（≥1），缺省 1
/// @param pageSize 每页大小（1..50），缺省 20
/// @author linqibin
/// @since 0.1.0
public record PortalPublicationSearchRequest(
    @Size(max = 200, message = "q 最长 200 字") String q,
    @Size(max = 200, message = "author 最长 200 字") String author,
    @Pattern(regexp = "\\d{1,15}", message = "pmid 须为 1–15 位数字") String pmid,
    @Size(max = 200, message = "doi 最长 200 字") String doi,
    @Min(value = 1000, message = "yearFrom 最小为 1000")
        @Max(value = 9999, message = "yearFrom 最大为 9999")
        Integer yearFrom,
    @Min(value = 1000, message = "yearTo 最小为 1000") @Max(value = 9999, message = "yearTo 最大为 9999")
        Integer yearTo,
    List<@Positive(message = "venue 须为正整数") Long> venue,
    List<String> type,
    List<
            @Pattern(
                regexp =
                    "(?i)SYSTEMATIC_REVIEW|RANDOMIZED_CONTROLLED_TRIAL|COHORT_OR_CASE_CONTROL"
                        + "|NON_SYSTEMATIC_REVIEW|CASE_REPORT|UNKNOWN",
                message = "evidence 须为证据等级名称")
            String>
        evidence,
    Boolean oa,
    List<String> lang,
    @Pattern(regexp = "(?i)latest|year", message = "sort 仅支持 latest 或 year") String sort,
    @Min(value = 1, message = "page 最小为 1") Integer page,
    @Min(value = 1, message = "pageSize 最小为 1") @Max(value = 50, message = "pageSize 最大为 50")
        Integer pageSize) {

  /// 紧凑构造器：各 List 归一化（trim + 去空白 token；venue 丢弃 null 元素）后防御性拷贝为不可变列表。
  public PortalPublicationSearchRequest {
    venue = venue == null ? List.of() : venue.stream().filter(Objects::nonNull).toList();
    type = normalize(type);
    evidence = normalize(evidence);
    lang = normalize(lang);
  }

  /// 年份区间自检：任一为 null 即通过，否则要求 yearFrom ≤ yearTo。
  ///
  /// @return 区间是否合法
  @AssertTrue(message = "yearFrom 不能大于 yearTo")
  public boolean isYearRangeValid() {
    return yearFrom == null || yearTo == null || yearFrom <= yearTo;
  }

  /// 归一化字符串列表：trim 每个元素并过滤空白 token；null 时返回空不可变列表。
  ///
  /// @param raw 原始列表，可空
  /// @return 归一化后的不可变列表
  private static List<String> normalize(List<String> raw) {
    if (raw == null) {
      return List.of();
    }
    return raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
