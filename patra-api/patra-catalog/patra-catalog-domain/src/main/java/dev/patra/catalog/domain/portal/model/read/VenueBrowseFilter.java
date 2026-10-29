package dev.patra.catalog.domain.portal.model.read;

import java.util.List;
import lombok.Builder;

/// 期刊浏览过滤参数（CQRS 读端）。
///
/// @param keyword 刊名前缀原始关键词；null 表示不过滤。LIKE 转义由读适配器（[VenueBrowseReadAdapter]）负责
/// @param sort 排序方式，null 时紧凑构造器自动替换为 [VenueBrowseSort.IMPACT_FACTOR]
/// @param subjects JCR 学科列表，空列表表示不过滤；多值之间为 OR 关系
/// @param jcrQuartiles JCR 分区列表（Q1-Q4），空列表表示不过滤；多值之间为 OR 关系
/// @param casQuartiles CAS 分区列表（Q1-Q4），空列表表示不过滤；多值之间为 OR 关系
/// @param casTop 是否 CAS 顶刊，null 表示不过滤
/// @param isOpenAccess 是否开放获取，null 表示不过滤
/// @param doaj 是否收录于 DOAJ，null 表示不过滤
/// @param countryCodes 国家/地区码列表，空列表表示不过滤；多值之间为 OR 关系
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueBrowseFilter(
    String keyword,
    VenueBrowseSort sort,
    List<String> subjects,
    List<String> jcrQuartiles,
    List<String> casQuartiles,
    Boolean casTop,
    Boolean isOpenAccess,
    Boolean doaj,
    List<String> countryCodes) {

  /// 紧凑构造器：sort 为 null 时默认 [VenueBrowseSort.IMPACT_FACTOR]；各 List 为 null 时替换为空不可变列表。
  public VenueBrowseFilter {
    if (sort == null) {
      sort = VenueBrowseSort.IMPACT_FACTOR;
    }
    subjects = subjects != null ? List.copyOf(subjects) : List.of();
    jcrQuartiles = jcrQuartiles != null ? List.copyOf(jcrQuartiles) : List.of();
    casQuartiles = casQuartiles != null ? List.copyOf(casQuartiles) : List.of();
    countryCodes = countryCodes != null ? List.copyOf(countryCodes) : List.of();
  }
}
