package dev.linqibin.patra.registry.app.service;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryStandardDisabledException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryStandardNotFoundException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryTypeNotFoundException;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemListResult;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryType;
import dev.linqibin.patra.registry.domain.model.vo.reference.ReferenceStandard;
import dev.linqibin.patra.registry.domain.port.DictionaryRepository;
import dev.linqibin.patra.registry.domain.port.ReferenceStandardRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 字典查询服务。
///
/// 职责：
///
/// - 批量解析外部值到规范字典项
/// - 根据来源标准是否为规范标准，选择不同的解析路径
/// - 返回解析状态，为后续 AI 或人工治理预留扩展空间
/// - 查询指定字典类型下所有启用的字典项列表（可含本地化标签）
///
/// **解析策略**：
///
/// 1. 如果 sourceStandard 是规范标准（canonical）：rawValue 直接匹配 item_code
/// 2. 如果 sourceStandard 不是规范标准：rawValue 匹配 alias.external_code
///
/// **设计说明**：
///
/// 本服务属于 CQRS 查询侧，不使用 `@Transactional` 注解。
/// 根据项目规范，查询操作无副作用，不需要事务管理等横切关注点。
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
  /// @param sourceStandard 来源标准（必填）
  /// @param rawValues 原始值列表
  /// @return 解析结果查询对象
  /// @throws DomainValidationException 当 typeCode 或 sourceStandard 为空时
  /// @throws DictionaryTypeNotFoundException 当字典类型不存在时
  /// @throws DictionaryStandardNotFoundException 当来源标准不存在或不属于该字典类型时
  /// @throws DictionaryStandardDisabledException 当来源标准已禁用时
  public DictionaryResolveQuery resolveBatch(
      String typeCode, String sourceStandard, List<String> rawValues) {
    String normalizedTypeCode = normalizeTypeCode(typeCode);
    String normalizedStandard = normalizeStandardCode(sourceStandard);

    DictionaryType type =
        repository
            .findTypeByCode(normalizedTypeCode)
            .orElseThrow(() -> new DictionaryTypeNotFoundException(normalizedTypeCode));

    ReferenceStandard standard =
        standardRepository
            .findByDictTypeCodeAndStandardCode(normalizedTypeCode, normalizedStandard)
            .orElseThrow(() -> new DictionaryStandardNotFoundException(normalizedStandard));
    if (!standard.enabled()) {
      throw new DictionaryStandardDisabledException(normalizedStandard);
    }

    List<String> values = rawValues == null ? List.of() : rawValues;
    if (values.isEmpty()) {
      return new DictionaryResolveQuery(normalizedTypeCode, normalizedStandard, List.of());
    }

    List<DictionaryResolveItemQuery> resultItems =
        standard.canonical()
            ? resolveByItemCode(type, values)
            : resolveByAlias(type, normalizedStandard, values);

    log.info(
        "字典批量解析完成 - typeCode: [{}], sourceStandard: [{}], canonical: [{}], size: [{}], resolved: [{}]",
        normalizedTypeCode,
        normalizedStandard,
        standard.canonical(),
        values.size(),
        resultItems.stream()
            .filter(item -> item.status() == DictionaryResolveStatus.RESOLVED)
            .count());

    return new DictionaryResolveQuery(normalizedTypeCode, normalizedStandard, resultItems);
  }

  /// 查询指定字典类型下所有启用的字典项。
  ///
  /// @param typeCode 字典类型代码
  /// @param labelStandard 本地化标签标准代码（可选），为 null 时不查询标签
  /// @return 字典项列表查询结果
  /// @throws DictionaryTypeNotFoundException 当字典类型不存在时
  /// @throws DictionaryStandardNotFoundException 当 labelStandard 不存在时
  /// @throws DictionaryStandardDisabledException 当 labelStandard 已禁用时
  public DictionaryItemListResult listItems(String typeCode, String labelStandard) {
    String normalizedTypeCode = normalizeTypeCode(typeCode);

    DictionaryType type =
        repository
            .findTypeByCode(normalizedTypeCode)
            .orElseThrow(() -> new DictionaryTypeNotFoundException(normalizedTypeCode));

    String validatedStandard = validateLabelStandard(normalizedTypeCode, labelStandard);
    String standardKeyForQuery =
        validatedStandard != null ? validatedStandard.toLowerCase(Locale.ROOT) : null;

    List<DictionaryItemSummary> items =
        repository.findAllEnabledItems(type.id(), standardKeyForQuery);

    log.info(
        "字典列表查询完成 - typeCode: [{}], labelStandard: [{}], itemCount: [{}]",
        normalizedTypeCode,
        validatedStandard,
        items.size());

    return new DictionaryItemListResult(normalizedTypeCode, validatedStandard, items);
  }

  /// 验证并归一化 labelStandard 参数。
  ///
  /// @param normalizedTypeCode 已归一化的字典类型代码
  /// @param labelStandard 原始标签标准代码，可为 null
  /// @return 归一化后的标准代码，或 null（当 labelStandard 为空时）
  private String validateLabelStandard(String normalizedTypeCode, String labelStandard) {
    if (!isNotBlank(labelStandard)) {
      return null;
    }
    String normalizedStandard = normalizeStandardCode(labelStandard);
    ReferenceStandard standard =
        standardRepository
            .findByDictTypeCodeAndStandardCode(normalizedTypeCode, normalizedStandard)
            .orElseThrow(() -> new DictionaryStandardNotFoundException(normalizedStandard));
    if (!standard.enabled()) {
      throw new DictionaryStandardDisabledException(normalizedStandard);
    }
    return normalizedStandard;
  }

  /// 通过 item_code 直接解析（规范标准路径）。
  ///
  /// @param type 字典类型
  /// @param values 原始值列表
  /// @return 解析结果列表
  private List<DictionaryResolveItemQuery> resolveByItemCode(
      DictionaryType type, List<String> values) {
    Set<String> codeCandidates =
        values.stream()
            .map(this::trimOrNull)
            .filter(this::isNotBlank)
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

    Map<String, DictionaryItem> itemsByCode =
        repository.findItemsByTypeAndCodes(type.id(), codeCandidates);

    List<DictionaryResolveItemQuery> results = new ArrayList<>(values.size());
    for (String rawValue : values) {
      String trimmed = trimOrNull(rawValue);
      if (!isNotBlank(trimmed)) {
        results.add(
            new DictionaryResolveItemQuery(rawValue, null, null, DictionaryResolveStatus.UNKNOWN));
        continue;
      }
      String normalizedCode = trimmed.toUpperCase(Locale.ROOT);
      DictionaryItem item = itemsByCode.get(normalizedCode);
      results.add(toResolveItem(rawValue, item));
    }
    return results;
  }

  /// 通过别名解析（非规范标准路径）。
  ///
  /// @param type 字典类型
  /// @param standardCode 标准代码
  /// @param values 原始值列表
  /// @return 解析结果列表
  private List<DictionaryResolveItemQuery> resolveByAlias(
      DictionaryType type, String standardCode, List<String> values) {
    Set<String> aliasCandidates =
        values.stream().map(this::trimOrNull).filter(this::isNotBlank).collect(Collectors.toSet());

    String aliasKey = standardCode.toLowerCase(Locale.ROOT);
    Map<String, DictionaryItem> itemsByAlias =
        repository.findItemsByAliases(type.id(), aliasKey, aliasCandidates);

    // 尝试使用连字符格式（例如 iso-3166-1-alpha2）
    String aliasKeyAlt = aliasKey.replace('_', '-');
    if (!aliasKeyAlt.equals(aliasKey)) {
      Map<String, DictionaryItem> altResults =
          repository.findItemsByAliases(type.id(), aliasKeyAlt, aliasCandidates);
      // 合并结果，优先使用下划线格式
      for (Map.Entry<String, DictionaryItem> entry : altResults.entrySet()) {
        itemsByAlias.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    List<DictionaryResolveItemQuery> results = new ArrayList<>(values.size());
    for (String rawValue : values) {
      String trimmed = trimOrNull(rawValue);
      if (!isNotBlank(trimmed)) {
        results.add(
            new DictionaryResolveItemQuery(rawValue, null, null, DictionaryResolveStatus.UNKNOWN));
        continue;
      }
      DictionaryItem item = itemsByAlias.get(trimmed);
      results.add(toResolveItem(rawValue, item));
    }
    return results;
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
    String normalized = DomainValidationException.notBlank(sourceStandard, "Source standard");
    return normalized.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
  }

  private String trimOrNull(String value) {
    return value == null ? null : value.trim();
  }

  private boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }
}
