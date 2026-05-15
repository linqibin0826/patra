package dev.linqibin.patra.catalog.infra.adapter.persistence;

import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.patra.catalog.domain.model.aggregate.AuthorAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorId;
import dev.linqibin.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import dev.linqibin.patra.catalog.domain.model.vo.author.Orcid;
import dev.linqibin.patra.catalog.domain.port.repository.AuthorRepository;
import dev.linqibin.patra.catalog.infra.persistence.converter.mapper.AuthorJpaMapper;
import dev.linqibin.patra.catalog.infra.persistence.dao.AuthorDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.AuthorEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.AuthorNameVariantEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.AuthorOrcidEntity;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 作者聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理作者聚合根的持久化，包括子实体（名字变体、ORCID）
/// - 处理业务键（normalizedKey）和 ORCID 的唯一性查询
/// - 支持软删除
///
/// **JPA 批量写入说明**：
///
/// - 使用 Spring Data JPA 的 `saveAll()` 进行批量保存
/// - ID 由 `SnowflakeIdGenerator` 雪花算法生成
/// - 审计字段由 JPA Auditing 自动填充
/// - 子实体通过 CascadeType.ALL 级联保存
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthorRepositoryAdapter implements AuthorRepository {

  private final AuthorDao authorDao;
  private final AuthorJpaMapper mapper;
  private final EntityManager entityManager;

  @Override
  public AuthorAggregate save(AuthorAggregate author) {
    if (author == null) {
      throw new IllegalArgumentException("作者聚合根不能为 null");
    }

    log.debug("保存作者：normalizedKey={}", author.getNormalizedKey());

    // 转换聚合根为实体
    AuthorEntity entity = mapper.toEntity(author);
    assignIdIfMissing(entity);

    // 处理子实体
    populateNameVariants(entity, author.getNameVariants());
    populateOrcids(entity, author.getOrcids());

    // 保存实体
    AuthorEntity saved = authorDao.save(entity);

    log.debug("作者保存完成，ID：{}", saved.getId());
    return mapper.toAggregate(saved);
  }

  @Override
  public void saveBatch(List<AuthorAggregate> authors) {
    if (authors == null || authors.isEmpty()) {
      log.warn("作者列表为空，跳过保存");
      return;
    }

    log.info("批量保存作者，数量：{}", authors.size());

    // 分离新增和更新
    List<AuthorAggregate> newAuthors =
        authors.stream().filter(AuthorAggregate::isTransient).toList();

    List<AuthorAggregate> existingAuthors = authors.stream().filter(a -> !a.isTransient()).toList();

    List<AuthorEntity> allEntities = new java.util.ArrayList<>();

    // 处理新增：直接转换为 Entity
    for (AuthorAggregate aggregate : newAuthors) {
      AuthorEntity entity = mapper.toEntity(aggregate);
      assignIdIfMissing(entity);
      populateNameVariants(entity, aggregate.getNameVariants());
      populateOrcids(entity, aggregate.getOrcids());
      allEntities.add(entity);
    }

    // 处理更新：批量加载现有 Entity，然后更新
    if (!existingAuthors.isEmpty()) {
      List<Long> ids = existingAuthors.stream().map(a -> a.getId().value()).toList();

      // 批量加载 managed entities
      List<AuthorEntity> loadedEntities = authorDao.findAllById(ids);
      Map<Long, AuthorEntity> entityMap =
          loadedEntities.stream()
              .collect(java.util.stream.Collectors.toMap(AuthorEntity::getId, e -> e));

      for (AuthorAggregate aggregate : existingAuthors) {
        AuthorEntity entity = entityMap.get(aggregate.getId().value());
        if (entity == null) {
          log.warn("跨批次更新失败：作者不存在，ID={}", aggregate.getId().value());
          continue;
        }

        // 更新核心属性
        updateEntityFromAggregate(entity, aggregate);

        // 更新子实体（orphanRemoval 会自动删除旧的）
        entity.getNameVariants().clear();
        entity.getOrcids().clear();

        allEntities.add(entity);
      }

      // 先 flush 删除操作，避免 INSERT 和 DELETE 顺序问题导致唯一约束冲突
      // Hibernate 默认顺序：INSERT → UPDATE → DELETE
      // 需要先执行 DELETE，才能 INSERT 具有相同唯一键的新记录
      entityManager.flush();

      // 现在添加新的子实体
      for (int i = 0; i < existingAuthors.size(); i++) {
        AuthorAggregate aggregate = existingAuthors.get(i);
        AuthorEntity entity = entityMap.get(aggregate.getId().value());
        if (entity != null) {
          populateNameVariants(entity, aggregate.getNameVariants());
          populateOrcids(entity, aggregate.getOrcids());
        }
      }
    }

    authorDao.saveAll(allEntities);

    log.info("作者批量保存完成：新增={}，更新={}", newAuthors.size(), existingAuthors.size());
  }

  /// 从聚合根更新 Entity 的核心属性。
  ///
  /// @param entity JPA 实体
  /// @param aggregate 聚合根
  private void updateEntityFromAggregate(AuthorEntity entity, AuthorAggregate aggregate) {
    entity.setNormalizedKey(aggregate.getNormalizedKey());
    entity.setDisplayName(aggregate.getDisplayName());
    entity.setStatus(aggregate.getStatus().getCode());
    entity.setProvenanceCode(aggregate.getProvenanceCode().getCode());
    entity.setLastSyncedAt(aggregate.getLastSyncedAt());
    // version 由 JPA @Version 自动管理，无需手动设置
  }

  @Override
  public Optional<AuthorAggregate> findById(AuthorId id) {
    if (id == null) {
      return Optional.empty();
    }
    return authorDao.findById(id.value()).map(mapper::toAggregate);
  }

  @Override
  public void deleteById(AuthorId id) {
    if (id == null) {
      return;
    }

    authorDao.deleteById(id.value());
    log.debug("作者软删除完成，ID：{}", id.value());
  }

  @Override
  public List<AuthorAggregate> findByNormalizedKey(String normalizedKey) {
    return authorDao.findByNormalizedKey(normalizedKey).stream().map(mapper::toAggregate).toList();
  }

  @Override
  public boolean existsByNormalizedKey(String normalizedKey) {
    return authorDao.existsByNormalizedKey(normalizedKey);
  }

  @Override
  public List<AuthorAggregate> findByNormalizedKeys(List<String> normalizedKeys) {
    return authorDao.findByNormalizedKeyIn(normalizedKeys).stream()
        .map(mapper::toAggregate)
        .toList();
  }

  @Override
  public Optional<AuthorAggregate> findByOrcid(String orcid) {
    return authorDao.findByOrcid(orcid).map(mapper::toAggregate);
  }

  @Override
  public boolean existsByOrcid(String orcid) {
    return authorDao.existsByOrcid(orcid);
  }

  @Override
  public Set<String> findExistingOrcids(Collection<String> orcids) {
    if (orcids == null || orcids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(authorDao.findExistingOrcids(orcids));
  }

  /// {@inheritDoc}
  ///
  /// **⚠️ 实现注意事项**：
  ///
  /// 此方法在返回前会调用 `entityManager.clear()` 清空整个一级缓存，
  /// 以避免后续 `saveBatch` 时与已加载的 Entity 发生冲突。
  ///
  /// **影响**：同一事务中已加载的其他实体状态将丢失。
  /// 调用方应确保在独立事务中使用此方法，或在调用后不依赖之前加载的实体。
  @Override
  public Map<String, AuthorAggregate> findAuthorsByAnyOrcid(Collection<String> orcids) {
    if (orcids == null || orcids.isEmpty()) {
      return Map.of();
    }

    // 查询所有匹配的作者（去重）
    List<AuthorEntity> matchedAuthors = authorDao.findByOrcidIn(orcids);

    if (matchedAuthors.isEmpty()) {
      return Map.of();
    }

    // 构建 ORCID → 作者 的映射
    // 每个作者的所有 ORCID 都作为键，指向同一个聚合根
    Map<String, AuthorAggregate> result = new HashMap<>();
    for (AuthorEntity entity : matchedAuthors) {
      AuthorAggregate aggregate = mapper.toAggregate(entity);
      // 将该作者的所有 ORCID 都作为键
      for (Orcid orcid : aggregate.getOrcids()) {
        result.put(orcid.value(), aggregate);
      }
    }

    // 清除一级缓存，避免后续 saveBatch 时与已加载的 Entity 发生冲突
    // 场景：查询的 Entity 在 session 中，saveBatch 转换后的 Entity 是新对象但有相同 ID
    entityManager.clear();

    return result;
  }

  @Override
  public boolean hasAnyData() {
    return authorDao.hasAnyData();
  }

  // ========== 私有方法 ==========

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(AuthorEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  /// 填充名字变体子实体（追加模式）。
  ///
  /// 将传入的名字变体添加到实体中，**不会**清空现有数据。
  /// 若需要替换现有数据，调用方应先调用 `entity.getNameVariants().clear()`。
  ///
  /// @param entity 作者实体
  /// @param variants 名字变体值对象列表
  private void populateNameVariants(AuthorEntity entity, List<AuthorNameVariant> variants) {
    if (variants == null || variants.isEmpty()) {
      return;
    }

    for (AuthorNameVariant variant : variants) {
      AuthorNameVariantEntity variantEntity = mapper.toNameVariantEntity(variant, entity);
      entity.addNameVariant(variantEntity);
    }
  }

  /// 填充 ORCID 子实体（追加模式）。
  ///
  /// 将传入的 ORCID 添加到实体中，**不会**清空现有数据。
  /// 若需要替换现有数据，调用方应先调用 `entity.getOrcids().clear()`。
  ///
  /// @param entity 作者实体
  /// @param orcids ORCID 值对象列表
  private void populateOrcids(AuthorEntity entity, List<Orcid> orcids) {
    if (orcids == null || orcids.isEmpty()) {
      return;
    }

    boolean isFirst = entity.getOrcids().isEmpty();
    for (Orcid orcid : orcids) {
      AuthorOrcidEntity orcidEntity = mapper.toOrcidEntity(orcid, entity, isFirst);
      entity.addOrcid(orcidEntity);
      isFirst = false;
    }
  }
}
