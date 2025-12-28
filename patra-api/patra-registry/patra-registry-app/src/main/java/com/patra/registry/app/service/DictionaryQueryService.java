package com.patra.registry.app.service;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.exception.dictionary.DictionaryStandardDisabledException;
import com.patra.registry.domain.exception.dictionary.DictionaryStandardNotFoundException;
import com.patra.registry.domain.exception.dictionary.DictionaryTypeNotFoundException;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.domain.model.vo.reference.ReferenceStandard;
import com.patra.registry.domain.port.DictionaryRepository;
import com.patra.registry.domain.port.ReferenceStandardRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 字典解析查询服务。
///
/// 职责：
///
/// - 批量解析外部值到规范字典项
/// - 优先匹配字典项代码,再匹配来源标准别名
/// - 返回解析状态,为后续 AI 或人工治理预留扩展空间
///
/// **设计说明**：
///
/// 本服务属于 CQRS 查询侧,不使用 `@Transactional` 注解。
/// 根据项目规范,查询操作无副作用,不需要事务管理等横切关注点。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryQueryService {

  private final DictionaryRepository repository;
  private final ReferenceStandardRepository standardRepository;

  /// 批量解析字典值。
  ///
  /// @param typeCode 字典类型代码
  /// @param sourceStandard 来源标准(可为空,为空时回退到全局别名)
  /// @param rawValues 原始值列表
  /// @return 解析结果查询对象
  public DictionaryResolveQuery resolveBatch(
      String typeCode, String sourceStandard, List<String> rawValues) {
    String normalizedTypeCode = normalizeTypeCode(typeCode);
    String resolvedStandard = normalizeStandardCode(sourceStandard);

    DictionaryType type =
        repository
            .findTypeByCode(normalizedTypeCode)
            .orElseThrow(() -> new DictionaryTypeNotFoundException(normalizedTypeCode));

    ReferenceStandard standard =
        standardRepository
            .findByCode(resolvedStandard)
            .orElseThrow(() -> new DictionaryStandardNotFoundException(resolvedStandard));
    if (!standard.enabled()) {
      throw new DictionaryStandardDisabledException(resolvedStandard);
    }

    List<String> values = rawValues == null ? List.of() : rawValues;
    if (values.isEmpty()) {
      return new DictionaryResolveQuery(normalizedTypeCode, resolvedStandard, List.of());
    }

    Set<String> codeCandidates =
        values.stream()
            .map(this::trimOrNull)
            .filter(this::isNotBlank)
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

    Set<String> aliasCandidates =
        values.stream().map(this::trimOrNull).filter(this::isNotBlank).collect(Collectors.toSet());

    Map<String, DictionaryItem> itemsByCode =
        repository.findItemsByTypeAndCodes(type.id(), codeCandidates);
    String standardAliasKey = resolvedStandard.toLowerCase(Locale.ROOT);
    Map<String, DictionaryItem> itemsByStandardAlias =
        repository.findItemsByAliases(type.id(), standardAliasKey, aliasCandidates);
    Map<String, DictionaryItem> itemsByStandardAliasAlt = Map.of();
    String standardAliasAltKey = standardAliasKey.replace('_', '-');
    if (!standardAliasAltKey.equals(standardAliasKey)) {
      itemsByStandardAliasAlt =
          repository.findItemsByAliases(type.id(), standardAliasAltKey, aliasCandidates);
    }
    Map<String, DictionaryItem> itemsByAlias =
        mergeAliasResults(itemsByStandardAlias, itemsByStandardAliasAlt);
    Map<String, DictionaryItem> itemsByGlobalAlias =
        standardAliasKey.equals(GLOBAL_STANDARD_ALIAS)
            ? Map.of()
            : repository.findItemsByAliases(type.id(), GLOBAL_STANDARD_ALIAS, aliasCandidates);

    List<DictionaryResolveItemQuery> resultItems = new ArrayList<>(values.size());
    for (String rawValue : values) {
      String trimmed = trimOrNull(rawValue);
      if (!isNotBlank(trimmed)) {
        resultItems.add(
            new DictionaryResolveItemQuery(rawValue, null, null, DictionaryResolveStatus.UNKNOWN));
        continue;
      }

      String normalizedCode = trimmed.toUpperCase(Locale.ROOT);
      DictionaryItem item = itemsByCode.get(normalizedCode);
      if (item == null) {
        item = itemsByAlias.get(trimmed);
      }
      if (item == null) {
        item = itemsByGlobalAlias.get(trimmed);
      }

      resultItems.add(toResolveItem(rawValue, item));
    }

    log.info(
        "字典批量解析完成 - typeCode: [{}], sourceStandard: [{}], size: [{}], resolved: [{}]",
        normalizedTypeCode,
        resolvedStandard,
        values.size(),
        resultItems.stream()
            .filter(item -> item.status() == DictionaryResolveStatus.RESOLVED)
            .count());

    return new DictionaryResolveQuery(normalizedTypeCode, resolvedStandard, resultItems);
  }

  private DictionaryResolveItemQuery toResolveItem(String rawValue, DictionaryItem item) {
    if (item == null) {
      return new DictionaryResolveItemQuery(rawValue, null, null, DictionaryResolveStatus.UNKNOWN);
    }
    DictionaryResolveStatus status =
        item.enabled() ? DictionaryResolveStatus.RESOLVED : DictionaryResolveStatus.DISABLED;
    return new DictionaryResolveItemQuery(rawValue, item.itemCode(), item.itemName(), status);
  }

  private String normalizeTypeCode(String typeCode) {
    String normalized = DomainValidationException.notBlank(typeCode, "Dictionary type code");
    return normalized.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeStandardCode(String sourceStandard) {
    if (sourceStandard == null || sourceStandard.isBlank()) {
      return GLOBAL_STANDARD_CODE;
    }
    String normalized =
        sourceStandard.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return GLOBAL_STANDARD_CODE;
    }
    return normalized;
  }

  private String trimOrNull(String value) {
    return value == null ? null : value.trim();
  }

  private boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }

  private Map<String, DictionaryItem> mergeAliasResults(
      Map<String, DictionaryItem> primary, Map<String, DictionaryItem> secondary) {
    if (secondary == null || secondary.isEmpty()) {
      return primary == null ? Map.of() : primary;
    }
    Map<String, DictionaryItem> merged = new HashMap<>(primary == null ? Map.of() : primary);
    for (Map.Entry<String, DictionaryItem> entry : secondary.entrySet()) {
      merged.putIfAbsent(entry.getKey(), entry.getValue());
    }
    return merged;
  }

  private static final String GLOBAL_STANDARD_CODE = "GLOBAL";
  private static final String GLOBAL_STANDARD_ALIAS = "global";
}
