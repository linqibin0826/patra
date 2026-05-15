package dev.linqibin.patra.ingest.infra.adapter.persistence;

import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import dev.linqibin.patra.ingest.domain.port.PlanSliceRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.PlanSliceJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.PlanSliceDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.PlanSliceEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 计划切片（PlanSlice）仓储实现，基于 JPA。
///
/// 职责：
///
/// - 根据 ID 是否存在插入或更新切片聚合根
/// - 批量保存，顺序调用以保持输入顺序
/// - 按 planId 查询所有切片，用于调度和重放
///
/// 设计与约束：
///
/// - 仓储层不进行复杂状态机验证；状态转换由应用服务控制
/// - 乐观锁：通过 JPA `@Version` 字段自动处理更新冲突
/// - 幂等性：调用方确保不重复创建相同业务语义的切片（如 sliceSignatureHash）
///
/// 日志策略：
///
/// - DEBUG：insert/update 记录 planId 和 hash（exprHash 表示表达式指纹）
/// - 无 INFO：避免高频路径产生噪音；错误由上层处理
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class PlanSliceRepositoryAdapter implements PlanSliceRepository {

  /// PlanSlice JPA Repository
  private final PlanSliceDao planSliceDao;

  /// 聚合根与 JPA 实体转换器
  private final PlanSliceJpaMapper planSliceJpaMapper;

  /// 保存单个切片。
  ///
  /// @param slice 切片聚合根
  /// @return 持久化后的聚合根，重新映射以包含生成的字段
  @Override
  public PlanSliceAggregate save(PlanSliceAggregate slice) {
    PlanSliceEntity entity = planSliceJpaMapper.toEntity(slice);

    if (slice.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug("slice insert planId={} hash={}", entity.getPlanId(), entity.getExprHash());
      }
    } else {
      // 更新：ValueObjectJpaEntity 无需乐观锁
      entity.setId(slice.getId().value());
      if (log.isDebugEnabled()) {
        log.debug(
            "slice update id={} planId={} hash={}",
            entity.getId(),
            entity.getPlanId(),
            entity.getExprHash());
      }
    }

    PlanSliceEntity saved = planSliceDao.save(entity);
    return planSliceJpaMapper.toAggregate(saved);
  }

  /// 批量保存切片。
  ///
  /// 顺序调用 {@link #save(PlanSliceAggregate)}，保持输入顺序。
  ///
  /// @param slices 切片集合
  /// @return 持久化结果，保持输入顺序
  @Override
  public List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices) {
    List<PlanSliceAggregate> persisted = new ArrayList<>(slices.size());
    for (PlanSliceAggregate slice : slices) {
      persisted.add(save(slice));
    }
    return persisted;
  }

  /// 根据计划 ID 查找切片。
  ///
  /// @param planId 计划 ID
  /// @return 切片列表，可能为空
  @Override
  public List<PlanSliceAggregate> findByPlanId(Long planId) {
    List<PlanSliceEntity> entities = planSliceDao.findByPlanId(planId);
    return entities.stream().map(planSliceJpaMapper::toAggregate).toList();
  }

  /// 根据切片 ID 查找切片。
  ///
  /// @param sliceId 切片 ID
  /// @return 切片聚合根（可选）
  @Override
  public Optional<PlanSliceAggregate> findById(Long sliceId) {
    if (sliceId == null) {
      return Optional.empty();
    }
    return planSliceDao.findById(sliceId).map(planSliceJpaMapper::toAggregate);
  }
}
