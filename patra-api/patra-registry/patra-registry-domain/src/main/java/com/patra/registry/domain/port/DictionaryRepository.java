package com.patra.registry.domain.port;

import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 字典查询仓储接口,提供字典解析所需的只读访问。
///
/// **职责**:
///
/// - 查询字典类型元数据
/// - 批量加载字典项与外部别名映射
///
/// @author linqibin
/// @since 0.1.0
public interface DictionaryRepository {

  /// 根据字典类型代码查询类型元数据。
  ///
  /// @param typeCode 字典类型代码
  /// @return 匹配的字典类型
  Optional<DictionaryType> findTypeByCode(String typeCode);

  /// 批量查询指定类型下的字典项。
  ///
  /// @param typeId 字典类型 ID
  /// @param itemCodes 字典项代码集合
  /// @return itemCode → DictionaryItem 的映射
  Map<String, DictionaryItem> findItemsByTypeAndCodes(Long typeId, Set<String> itemCodes);

  /// 批量查询指定来源标准的外部映射并返回对应字典项。
  ///
  /// @param typeId 字典类型 ID
  /// @param sourceStandard 来源标准
  /// @param externalCodes 外部代码集合
  /// @return externalCode → DictionaryItem 的映射
  Map<String, DictionaryItem> findItemsByAliases(
      Long typeId, String sourceStandard, Set<String> externalCodes);
}
