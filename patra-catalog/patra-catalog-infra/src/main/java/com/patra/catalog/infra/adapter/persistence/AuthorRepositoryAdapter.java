package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import com.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.domain.port.repository.AuthorRepository;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.AuthorJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.AuthorDao;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorNameVariantEntity;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorOrcidEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    List<AuthorEntity> entities =
        authors.stream()
            .map(
                aggregate -> {
                  AuthorEntity entity = mapper.toEntity(aggregate);
                  assignIdIfMissing(entity);
                  populateNameVariants(entity, aggregate.getNameVariants());
                  populateOrcids(entity, aggregate.getOrcids());
                  return entity;
                })
            .toList();

    authorDao.saveAll(entities);

    log.info("作者批量保存完成，共 {} 条", entities.size());
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

    authorDao
        .findById(id.value())
        .ifPresent(
            entity -> {
              entity.setDeletedAt(Instant.now());
              authorDao.save(entity);
              log.debug("作者软删除完成，ID：{}", id.value());
            });
  }

  @Override
  public Optional<AuthorAggregate> findByNormalizedKey(String normalizedKey) {
    return authorDao.findByNormalizedKey(normalizedKey).map(mapper::toAggregate);
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

  /// 填充名字变体子实体。
  ///
  /// @param entity 作者实体
  /// @param variants 名字变体值对象列表
  private void populateNameVariants(AuthorEntity entity, List<AuthorNameVariant> variants) {
    if (variants == null || variants.isEmpty()) {
      return;
    }

    entity.getNameVariants().clear();
    for (AuthorNameVariant variant : variants) {
      AuthorNameVariantEntity variantEntity = mapper.toNameVariantEntity(variant, entity);
      entity.addNameVariant(variantEntity);
    }
  }

  /// 填充 ORCID 子实体。
  ///
  /// @param entity 作者实体
  /// @param orcids ORCID 值对象列表
  private void populateOrcids(AuthorEntity entity, List<Orcid> orcids) {
    if (orcids == null || orcids.isEmpty()) {
      return;
    }

    entity.getOrcids().clear();
    boolean isFirst = true;
    for (Orcid orcid : orcids) {
      AuthorOrcidEntity orcidEntity = mapper.toOrcidEntity(orcid, entity, isFirst);
      entity.addOrcid(orcidEntity);
      isFirst = false;
    }
  }
}
