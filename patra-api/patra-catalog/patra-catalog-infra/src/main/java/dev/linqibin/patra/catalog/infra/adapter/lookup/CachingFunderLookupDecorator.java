package dev.linqibin.patra.catalog.infra.adapter.lookup;

import dev.linqibin.patra.catalog.domain.port.lookup.FunderLookupPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.OrganizationExternalIdDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/// 资助机构查找缓存包装器。
///
/// 为 `FunderLookupPort` 提供内存缓存能力，优化批处理场景下的查询性能。
///
/// **缓存策略**：
///
/// - 双索引：FundRef ID + ROR ID → Organization ID
/// - Negative Cache：记录未找到的标识符，避免重复查询
/// - 预热支持：可批量加载 FundRef/ROR 映射关系
///
/// **注意**：此类不是 Spring Bean，需要在使用时手动创建实例。
/// 缓存生命周期由调用方控制（如 Step 级别、Request 级别等）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class CachingFunderLookupDecorator implements FunderLookupPort {

  /// 外部标识符类型常量：Crossref Funder Registry ID
  private static final String ID_TYPE_FUNDREF = "FUNDREF";

  private final OrganizationExternalIdDao externalIdDao;
  private final OrganizationDao organizationDao;

  /// FundRef ID → Organization ID 缓存
  private final Map<String, Long> fundRefIdCache = new ConcurrentHashMap<>();

  /// ROR ID → Organization ID 缓存
  private final Map<String, Long> rorIdCache = new ConcurrentHashMap<>();

  /// 机构名称 → Organization ID 缓存
  private final Map<String, Long> nameCache = new ConcurrentHashMap<>();

  /// 已查询但未找到的标识符（避免重复查询）
  private final Set<String> notFoundIdentifiers = ConcurrentHashMap.newKeySet();

  /// 已查询但未找到的名称（避免重复查询）
  private final Set<String> notFoundNames = ConcurrentHashMap.newKeySet();

  /// 构造函数。
  ///
  /// @param externalIdDao 外部标识符 DAO
  /// @param organizationDao 机构 DAO
  public CachingFunderLookupDecorator(
      OrganizationExternalIdDao externalIdDao, OrganizationDao organizationDao) {
    this.externalIdDao = externalIdDao;
    this.organizationDao = organizationDao;
  }

  @Override
  public Optional<Long> findByIdentifier(String funderIdentifier) {
    if (funderIdentifier == null || funderIdentifier.isBlank()) {
      return Optional.empty();
    }

    String normalizedId = funderIdentifier.trim();

    // 检查 Negative Cache
    if (notFoundIdentifiers.contains(normalizedId)) {
      return Optional.empty();
    }

    // 1. 检查 FundRef ID 缓存
    Long cachedOrgId = fundRefIdCache.get(normalizedId);
    if (cachedOrgId != null) {
      return Optional.of(cachedOrgId);
    }

    // 2. 检查 ROR ID 缓存
    cachedOrgId = rorIdCache.get(normalizedId);
    if (cachedOrgId != null) {
      return Optional.of(cachedOrgId);
    }

    // 3. 查询数据库：尝试作为 FundRef ID
    Optional<Long> result = findByFundRefIdFromDb(normalizedId);
    if (result.isPresent()) {
      fundRefIdCache.put(normalizedId, result.get());
      return result;
    }

    // 4. 查询数据库：尝试作为 ROR ID
    result = findByRorIdFromDb(normalizedId);
    if (result.isPresent()) {
      rorIdCache.put(normalizedId, result.get());
      return result;
    }

    // 5. 记录未找到
    notFoundIdentifiers.add(normalizedId);
    return Optional.empty();
  }

  @Override
  public Optional<Long> findByName(String funderName) {
    if (funderName == null || funderName.isBlank()) {
      return Optional.empty();
    }

    String normalizedName = funderName.trim();

    // 检查 Negative Cache
    if (notFoundNames.contains(normalizedName)) {
      return Optional.empty();
    }

    // 检查缓存
    Long cachedOrgId = nameCache.get(normalizedName);
    if (cachedOrgId != null) {
      return Optional.of(cachedOrgId);
    }

    // 查询数据库
    Optional<OrganizationEntity> orgOpt = organizationDao.findByDisplayName(normalizedName);
    if (orgOpt.isPresent()) {
      Long orgId = orgOpt.get().getId();
      nameCache.put(normalizedName, orgId);
      return Optional.of(orgId);
    }

    notFoundNames.add(normalizedName);
    return Optional.empty();
  }

  /// 预热 FundRef ID 缓存。
  ///
  /// 批量加载所有 FundRef 类型的外部标识符映射关系。
  /// 建议在 Step 初始化时调用一次。
  public void warmupFundRefCache() {
    log.info("开始预热 FunderLookup FundRef 缓存...");
    List<OrganizationExternalIdEntity> fundRefIds = externalIdDao.findAllByIdType(ID_TYPE_FUNDREF);

    for (OrganizationExternalIdEntity entity : fundRefIds) {
      fundRefIdCache.put(entity.getPreferredValue(), entity.getOrgId());
    }

    log.info("FunderLookup FundRef 缓存预热完成，加载 {} 条映射", fundRefIdCache.size());
  }

  /// 获取缓存统计信息。
  ///
  /// @return 缓存统计描述
  public String getStats() {
    return "FunderLookupCache[fundRefIdCache=%d, rorIdCache=%d, nameCache=%d, notFound=%d]"
        .formatted(
            fundRefIdCache.size(),
            rorIdCache.size(),
            nameCache.size(),
            notFoundIdentifiers.size() + notFoundNames.size());
  }

  /// 清空缓存。
  public void clear() {
    fundRefIdCache.clear();
    rorIdCache.clear();
    nameCache.clear();
    notFoundIdentifiers.clear();
    notFoundNames.clear();
    log.debug("FunderLookup 缓存已清空");
  }

  /// 通过 FundRef ID 从数据库查找。
  private Optional<Long> findByFundRefIdFromDb(String fundRefId) {
    return externalIdDao
        .findByIdTypeAndPreferredValue(ID_TYPE_FUNDREF, fundRefId)
        .map(OrganizationExternalIdEntity::getOrgId);
  }

  /// 通过 ROR ID 从数据库查找。
  private Optional<Long> findByRorIdFromDb(String rorId) {
    return organizationDao.findByRorId(rorId).map(OrganizationEntity::getId);
  }
}
