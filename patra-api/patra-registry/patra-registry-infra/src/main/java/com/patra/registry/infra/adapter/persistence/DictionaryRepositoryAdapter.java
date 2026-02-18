package com.patra.registry.infra.adapter.persistence;

import com.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.domain.port.DictionaryRepository;
import com.patra.registry.infra.adapter.persistence.converter.mapper.DictionaryJpaMapper;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemAliasDao;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemDao;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictTypeDao;
import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemAliasEntity;
import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemEntity;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 字典查询仓储实现,基于 JPA。
///
/// 负责根据类型/别名查询字典项,为解析服务提供数据支持。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class DictionaryRepositoryAdapter implements DictionaryRepository {

  private final SysDictTypeDao typeDao;
  private final SysDictItemDao itemDao;
  private final SysDictItemAliasDao aliasDao;
  private final DictionaryJpaMapper mapper;

  /// 根据字典类型代码查询类型元数据。
  ///
  /// @param typeCode 字典类型代码
  /// @return 匹配的字典类型
  @Override
  public Optional<DictionaryType> findTypeByCode(String typeCode) {
    if (typeCode == null || typeCode.isBlank()) {
      return Optional.empty();
    }
    return typeDao.findByTypeCode(typeCode).map(mapper::toDomain);
  }

  /// 批量查询指定类型下的字典项。
  ///
  /// @param typeId 字典类型 ID
  /// @param itemCodes 字典项代码集合
  /// @return itemCode → DictionaryItem 的映射
  @Override
  public Map<String, DictionaryItem> findItemsByTypeAndCodes(Long typeId, Set<String> itemCodes) {
    if (typeId == null || itemCodes == null || itemCodes.isEmpty()) {
      return Map.of();
    }
    List<SysDictItemEntity> entities = itemDao.findByTypeIdAndItemCodeIn(typeId, itemCodes);
    return entities.stream()
        .map(mapper::toDomain)
        .collect(Collectors.toMap(DictionaryItem::itemCode, Function.identity(), (a, b) -> a));
  }

  /// 批量查询指定来源标准的外部映射并返回对应字典项。
  ///
  /// @param typeId 字典类型 ID
  /// @param sourceStandard 来源标准
  /// @param externalCodes 外部代码集合
  /// @return externalCode → DictionaryItem 的映射
  @Override
  public Map<String, DictionaryItem> findItemsByAliases(
      Long typeId, String sourceStandard, Set<String> externalCodes) {
    if (typeId == null
        || sourceStandard == null
        || sourceStandard.isBlank()
        || externalCodes == null
        || externalCodes.isEmpty()) {
      return Map.of();
    }

    List<SysDictItemAliasEntity> aliasEntities =
        aliasDao.findBySourceStandardAndExternalCodeIn(sourceStandard, externalCodes);
    if (aliasEntities.isEmpty()) {
      return Map.of();
    }

    Set<Long> itemIds =
        aliasEntities.stream().map(SysDictItemAliasEntity::getItemId).collect(Collectors.toSet());
    Map<Long, DictionaryItem> itemsById = loadItemsById(itemIds);

    Map<String, DictionaryItem> result = new HashMap<>();
    for (SysDictItemAliasEntity alias : aliasEntities) {
      DictionaryItem item = itemsById.get(alias.getItemId());
      if (item == null) {
        continue;
      }
      if (!typeId.equals(item.typeId())) {
        log.debug(
            "Ignore alias because item type mismatch, aliasStandard [{}], externalCode [{}], itemTypeId [{}]",
            alias.getSourceStandard(),
            alias.getExternalCode(),
            item.typeId());
        continue;
      }
      result.put(alias.getExternalCode(), item);
    }
    return result;
  }

  /// 查询指定类型下所有启用的字典项，附带可选的本地化标签。
  ///
  /// @param typeId 字典类型 ID
  /// @param labelStandard 标签标准代码（小写），为 null 时不查询标签
  /// @return 按 displayOrder 和 itemCode 排序的字典项摘要列表
  @Override
  public List<DictionaryItemSummary> findAllEnabledItems(Long typeId, String labelStandard) {
    if (typeId == null) {
      return List.of();
    }

    List<SysDictItemEntity> items =
        itemDao.findByTypeIdAndEnabledTrueOrderByDisplayOrderAscItemCodeAsc(typeId);
    if (items.isEmpty()) {
      return List.of();
    }

    // 构建 itemId → label 映射（当 labelStandard 非空时）
    Map<Long, String> labelByItemId = Map.of();
    if (labelStandard != null && !labelStandard.isBlank()) {
      Set<Long> itemIds = items.stream().map(SysDictItemEntity::getId).collect(Collectors.toSet());
      List<SysDictItemAliasEntity> aliases =
          aliasDao.findBySourceStandardAndItemIdIn(labelStandard, itemIds);
      labelByItemId =
          aliases.stream()
              .collect(
                  Collectors.toMap(
                      SysDictItemAliasEntity::getItemId,
                      SysDictItemAliasEntity::getExternalCode,
                      (a, b) -> a));
    }

    Map<Long, String> finalLabelByItemId = labelByItemId;
    return items.stream()
        .map(
            entity ->
                new DictionaryItemSummary(
                    entity.getItemCode(),
                    entity.getItemName(),
                    finalLabelByItemId.get(entity.getId()),
                    entity.getDisplayOrder() != null ? entity.getDisplayOrder() : 0))
        .toList();
  }

  /// 批量加载字典项并构建 ID → DictionaryItem 的映射。
  ///
  /// @param itemIds 字典项 ID 集合
  /// @return ID → DictionaryItem 的映射
  private Map<Long, DictionaryItem> loadItemsById(Collection<Long> itemIds) {
    if (itemIds == null || itemIds.isEmpty()) {
      return Map.of();
    }
    List<SysDictItemEntity> entities = itemDao.findByIdIn(itemIds);
    return entities.stream()
        .map(mapper::toDomain)
        .collect(Collectors.toMap(DictionaryItem::id, Function.identity(), (a, b) -> a));
  }
}
