package dev.linqibin.patra.catalog.domain.model.read.portal;

import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import java.util.List;
import lombok.Builder;

/// 文献检索过滤参数（CQRS 读端）。
///
/// 全部字段为"原始意图"：LIKE 转义、DOI 前缀归一化、类型小写化由读适配器负责。
///
/// @param keyword 标题子串关键词；null 表示不过滤
/// @param author 作者展示名子串；null 表示不过滤
/// @param pmid PubMed ID 精确值；null 表示不过滤
/// @param doi DOI 精确值（可带 URL / doi: 前缀）；null 表示不过滤
/// @param yearFrom 出版年份下限（含）；null 表示不限
/// @param yearTo 出版年份上限（含）；null 表示不限
/// @param venueIds 期刊 ID 列表，空表示不过滤；多值 OR
/// @param types 文献类型完整值列表，空表示不过滤；多值 OR，大小写不敏感
/// @param evidenceLevels 证据等级列表（按每篇最强档等值），空表示不过滤；多值 OR
/// @param isOpenAccess 是否开放获取；null 表示不过滤
/// @param languages 语言基码（language_base）列表，空表示不过滤；多值 OR
/// @param sort 排序方式，null 时替换为 [PublicationSearchSort.LATEST]
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationSearchFilter(
    String keyword,
    String author,
    String pmid,
    String doi,
    Integer yearFrom,
    Integer yearTo,
    List<Long> venueIds,
    List<String> types,
    List<EvidenceLevel> evidenceLevels,
    Boolean isOpenAccess,
    List<String> languages,
    PublicationSearchSort sort) {

  /// 紧凑构造器：sort 为 null 时默认 LATEST；各 List 为 null 时替换为空不可变列表，否则防御性拷贝。
  public PublicationSearchFilter {
    if (sort == null) {
      sort = PublicationSearchSort.LATEST;
    }
    venueIds = venueIds != null ? List.copyOf(venueIds) : List.of();
    types = types != null ? List.copyOf(types) : List.of();
    evidenceLevels = evidenceLevels != null ? List.copyOf(evidenceLevels) : List.of();
    languages = languages != null ? List.copyOf(languages) : List.of();
  }
}
