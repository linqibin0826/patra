package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.infra.persistence.converter.mapper.MeshQualifierJpaMapper;
import com.patra.catalog.infra.persistence.dao.MeshQualifierDao;
import com.patra.catalog.infra.persistence.entity.MeshQualifierEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 限定词聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理 MeSH 限定词聚合根的持久化
/// - 批量保存限定词到数据库
/// - 聚合根与 JPA Entity 的转换
///
/// **JPA 批量写入说明**：
///
/// - 使用 Spring Data JPA 的 `saveAll()` 进行批量保存
/// - Hibernate 配置了 `batch_size=500`、`order_inserts=true` 优化批量性能
/// - ID 由 `SnowflakeIdGenerator` 雪花算法生成
/// - 审计字段（createdAt 等）由 JPA Auditing 自动填充
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshQualifierRepositoryAdapter implements MeshQualifierRepository {

  private final MeshQualifierDao jpaRepository;
  private final MeshQualifierJpaMapper jpaConverter;

  @Override
  public void saveBatch(List<MeshQualifierAggregate> qualifiers) {
    if (qualifiers == null || qualifiers.isEmpty()) {
      log.warn("限定词列表为空，跳过保存");
      return;
    }

    log.info("批量保存限定词，数量：{}", qualifiers.size());

    // 转换为 JPA Entity 列表，并为没有 ID 的实体分配雪花 ID
    List<MeshQualifierEntity> entities =
        qualifiers.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    // 批量保存（审计字段由 JPA Auditing 自动填充）
    jpaRepository.saveAll(entities);

    log.info("限定词批量保存完成，共 {} 条", entities.size());
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// JPA 不使用数据库自增 ID（为保证批量插入性能），
  /// 需要在持久化前手动为新实体分配 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(MeshQualifierEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  @Override
  public boolean hasAnyData() {
    return jpaRepository.hasAnyData();
  }

  @Override
  public Map<String, String> findAllByNameIn(Collection<String> names) {
    if (names == null || names.isEmpty()) {
      return Map.of();
    }

    List<MeshQualifierEntity> entities = jpaRepository.findAllByNameIn(names);

    // 转换为 name → ui 映射
    return entities.stream()
        .collect(
            Collectors.toMap(
                MeshQualifierEntity::getName,
                MeshQualifierEntity::getUi,
                (existing, replacement) -> existing // 保留首个匹配
                ));
  }
}
