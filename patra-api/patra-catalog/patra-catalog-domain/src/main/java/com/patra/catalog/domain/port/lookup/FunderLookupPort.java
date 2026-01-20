package com.patra.catalog.domain.port.lookup;

import java.util.Optional;

/// 资助机构查找端口。
///
/// 提供资助机构（Funder）标识符匹配能力，将 PubMed/OpenAlex 等数据源中的
/// 资助机构信息匹配到 `cat_organization` 表中的记录。
///
/// **适用场景**：
///
/// - API 单次查询
/// - 批量数据导入
/// - 事件处理
///
/// 实现层可自由选择缓存策略（无缓存、内存缓存、分布式缓存等）。
///
/// **匹配优先级**：
///
/// 1. FundRef ID（Crossref Funder Registry ID，最可靠）
/// 2. ROR ID（Research Organization Registry）
/// 3. 机构名称模糊匹配（备选方案，可能不准确）
///
/// **资助机构标识符示例**：
///
/// - FundRef ID: `100000002`（NIH）, `501100001809`（NSFC）
/// - ROR ID: `01cwqze88`（NIH）
///
/// @author linqibin
/// @since 0.1.0
public interface FunderLookupPort {

  /// 通过资助机构标识符查找机构 ID。
  ///
  /// 支持 FundRef ID 和 ROR ID 两种标识符类型，按以下优先级匹配：
  /// 1. 先尝试作为 FundRef ID 精确匹配
  /// 2. 再尝试作为 ROR ID 精确匹配
  ///
  /// @param funderIdentifier 资助机构标识符（FundRef ID 或 ROR ID）
  /// @return 机构 ID，未找到返回 empty
  Optional<Long> findByIdentifier(String funderIdentifier);

  /// 通过机构名称模糊匹配查找机构 ID。
  ///
  /// **注意**：名称匹配可能不准确，仅作为标识符匹配失败后的备选方案。
  /// 建议在批处理场景中跳过此方法以提高性能。
  ///
  /// @param funderName 资助机构名称
  /// @return 机构 ID，未找到返回 empty
  Optional<Long> findByName(String funderName);

  /// 按优先级匹配资助机构（标识符 → 名称）。
  ///
  /// 匹配优先级：FundRef ID → ROR ID → 名称匹配
  ///
  /// @param funderIdentifier 资助机构标识符（FundRef ID 或 ROR ID）
  /// @param funderName 资助机构名称（作为备选）
  /// @return 机构 ID，如果所有方式都无法匹配返回 empty
  default Optional<Long> findByPriority(String funderIdentifier, String funderName) {
    // 1. 优先使用标识符匹配
    if (funderIdentifier != null && !funderIdentifier.isBlank()) {
      Optional<Long> result = findByIdentifier(funderIdentifier);
      if (result.isPresent()) {
        return result;
      }
    }

    // 2. 备选：名称匹配
    if (funderName != null && !funderName.isBlank()) {
      return findByName(funderName);
    }

    return Optional.empty();
  }
}
