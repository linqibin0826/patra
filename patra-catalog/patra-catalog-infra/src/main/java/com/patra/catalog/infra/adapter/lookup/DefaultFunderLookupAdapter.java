package com.patra.catalog.infra.adapter.lookup;

import com.patra.catalog.domain.port.lookup.FunderLookupPort;
import com.patra.catalog.infra.persistence.dao.OrganizationDao;
import com.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import com.patra.catalog.infra.persistence.entity.OrganizationEntity;
import com.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 默认资助机构查找适配器（无缓存）。
///
/// 直接查询数据库实现 FunderLookupPort 接口，适用于 API 单次查询场景。
/// 对于批处理场景，建议使用 `BatchFunderLookupAdapter`（带缓存）。
///
/// **匹配逻辑**：
///
/// 1. 优先通过 FundRef ID 查找（`cat_organization_external_id.id_type = 'FUNDREF'`）
/// 2. 其次通过 ROR ID 查找（`cat_organization.ror_id` 或 `external_id.id_type = 'ROR'`）
/// 3. 最后通过机构名称模糊匹配（`cat_organization.display_name`）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFunderLookupAdapter implements FunderLookupPort {

  /// 外部标识符类型常量：Crossref Funder Registry ID
  private static final String ID_TYPE_FUNDREF = "FUNDREF";

  private final OrganizationExternalIdDao externalIdDao;
  private final OrganizationDao organizationDao;

  @Override
  public Optional<Long> findByIdentifier(String funderIdentifier) {
    if (funderIdentifier == null || funderIdentifier.isBlank()) {
      return Optional.empty();
    }

    String normalizedId = funderIdentifier.trim();

    // 1. 尝试作为 FundRef ID 查找
    Optional<Long> result = findByFundRefId(normalizedId);
    if (result.isPresent()) {
      log.debug("通过 FundRef ID 匹配到机构：identifier={}, orgId={}", normalizedId, result.get());
      return result;
    }

    // 2. 尝试作为 ROR ID 查找
    result = findByRorId(normalizedId);
    if (result.isPresent()) {
      log.debug("通过 ROR ID 匹配到机构：identifier={}, orgId={}", normalizedId, result.get());
      return result;
    }

    log.debug("无法通过标识符匹配机构：identifier={}", normalizedId);
    return Optional.empty();
  }

  @Override
  public Optional<Long> findByName(String funderName) {
    if (funderName == null || funderName.isBlank()) {
      return Optional.empty();
    }

    String normalizedName = funderName.trim();

    // 通过显示名称精确匹配（暂不支持模糊匹配，以保证数据准确性）
    Optional<OrganizationEntity> orgOpt = organizationDao.findByDisplayName(normalizedName);
    if (orgOpt.isPresent()) {
      log.debug("通过名称匹配到机构：name={}, orgId={}", normalizedName, orgOpt.get().getId());
      return Optional.of(orgOpt.get().getId());
    }

    log.debug("无法通过名称匹配机构：name={}", normalizedName);
    return Optional.empty();
  }

  /// 通过 FundRef ID 查找机构。
  ///
  /// @param fundRefId FundRef ID（如 "100000002"）
  /// @return 机构 ID
  private Optional<Long> findByFundRefId(String fundRefId) {
    return externalIdDao
        .findByIdTypeAndPreferredValue(ID_TYPE_FUNDREF, fundRefId)
        .map(OrganizationExternalIdEntity::getOrgId);
  }

  /// 通过 ROR ID 查找机构。
  ///
  /// 优先查询主表的 ror_id 字段，因为它是唯一索引，查询效率更高。
  ///
  /// @param rorId ROR ID（如 "01cwqze88"）
  /// @return 机构 ID
  private Optional<Long> findByRorId(String rorId) {
    // 优先查主表的 ror_id 字段
    return organizationDao.findByRorId(rorId).map(OrganizationEntity::getId);
  }
}
