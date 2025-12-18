package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.port.repository.AuthorRepository;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.AuthorJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.AuthorDao;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 作者聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理作者聚合根的持久化
/// - 处理 ORCID 唯一性检查
/// - 支持去重查询和相似度匹配
/// - 提供按标识符（ORCID/ResearcherID/ScopusID）查询
///
/// **JPA 批量写入说明**：
///
/// - 使用 Spring Data JPA 的 `saveAll()` 进行批量保存
/// - ID 由 `SnowflakeIdGenerator` 雪花算法生成
/// - 审计字段由 JPA Auditing 自动填充
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthorRepositoryAdapter implements AuthorRepository {

  private final AuthorDao jpaRepository;
  private final AuthorJpaMapper jpaConverter;

  /// 保存单个作者聚合根。
  ///
  /// @param author 作者聚合根
  /// @return 保存后的聚合根（含 ID）
  public AuthorAggregate save(AuthorAggregate author) {
    if (author == null) {
      log.warn("作者为空，跳过保存");
      return null;
    }

    log.debug("保存作者：{}", author.getDisplayName());

    AuthorEntity entity = jpaConverter.toEntity(author);
    assignIdIfMissing(entity);

    AuthorEntity saved = jpaRepository.save(entity);

    log.debug("作者保存完成，ID：{}", saved.getId());
    return jpaConverter.toAggregate(saved);
  }

  /// 批量保存作者聚合根。
  ///
  /// @param authors 作者聚合根列表
  public void saveBatch(List<AuthorAggregate> authors) {
    if (authors == null || authors.isEmpty()) {
      log.warn("作者列表为空，跳过保存");
      return;
    }

    log.info("批量保存作者，数量：{}", authors.size());

    List<AuthorEntity> entities =
        authors.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    jpaRepository.saveAll(entities);

    log.info("作者批量保存完成，共 {} 条", entities.size());
  }

  /// 根据 ID 查询作者。
  ///
  /// @param id 作者 ID
  /// @return 作者聚合根（可选）
  public Optional<AuthorAggregate> findById(Long id) {
    return jpaRepository.findById(id).map(jpaConverter::toAggregate);
  }

  /// 根据 ORCID 查询作者。
  ///
  /// @param orcid ORCID 标识符
  /// @return 作者聚合根（可选）
  public Optional<AuthorAggregate> findByOrcid(String orcid) {
    return jpaRepository.findByOrcid(orcid).map(jpaConverter::toAggregate);
  }

  /// 根据邮箱查询作者。
  ///
  /// @param email 邮箱地址
  /// @return 作者聚合根列表
  public List<AuthorAggregate> findByEmail(String email) {
    return jpaRepository.findByEmail(email).stream().map(jpaConverter::toAggregate).toList();
  }

  /// 根据去重键查询作者。
  ///
  /// @param dedupKey 去重键
  /// @return 作者聚合根列表
  public List<AuthorAggregate> findByDedupKey(String dedupKey) {
    return jpaRepository.findByDedupKey(dedupKey).stream().map(jpaConverter::toAggregate).toList();
  }

  /// 检查 ORCID 是否已存在。
  ///
  /// @param orcid ORCID 标识符
  /// @return true 如果已存在
  public boolean existsByOrcid(String orcid) {
    return jpaRepository.existsByOrcid(orcid);
  }

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  public boolean hasAnyData() {
    return jpaRepository.hasAnyData();
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(AuthorEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
