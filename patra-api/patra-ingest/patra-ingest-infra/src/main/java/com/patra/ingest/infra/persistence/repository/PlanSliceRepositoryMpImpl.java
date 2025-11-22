package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.persistence.converter.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 计划切片（PlanSlice）仓储实现,基于 MyBatis-Plus。
///
/// 职责:
///
/// - 根据 ID 是否存在插入或更新切片聚合根
///   - 批量保存,顺序调用以保持输入顺序
///   - 按 planId 查询所有切片,用于调度和重放
///
/// 设计与约束:
///
/// - 仓储层不进行复杂状态机验证;状态转换由应用服务控制
///   - 乐观锁: 如 DO 包含 version 字段,由 MyBatis-Plus 自动处理更新;可扩展条件更新以应对未来并发场景
///   - 幂等性: 调用方确保不重复创建相同业务语义的切片(如 sliceSignatureHash)
///
/// 日志策略:
///
/// - DEBUG: insert/update 记录 planId 和 hash (exprHash 表示表达式指纹)
///   - 无 INFO: 避免高频路径产生噪音;错误由上层处理
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class PlanSliceRepositoryMpImpl implements PlanSliceRepository {

  private final PlanSliceMapper mapper;
  private final PlanSliceConverter converter;

  /// 保存单个切片。
  ///
  /// @param slice 切片聚合根
  /// @return 持久化后的聚合根,重新映射以包含生成的字段
  @Override
  public PlanSliceAggregate save(PlanSliceAggregate slice) {
    PlanSliceDO entity = converter.toEntity(slice);
    if (entity.getId() == null) {
      if (log.isDebugEnabled()) {
        log.debug("slice insert planId={} hash={}", entity.getPlanId(), entity.getExprHash());
      }
      mapper.insert(entity);
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "slice update id={} planId={} hash={}",
            entity.getId(),
            entity.getPlanId(),
            entity.getExprHash());
      }
      mapper.updateById(entity);
    }
    return converter.toAggregate(entity);
  }

  /// 批量保存切片。
  ///
  /// 顺序调用 {@link #save(PlanSliceAggregate)},保持输入顺序。
  ///
  /// @param slices 切片集合
  /// @return 持久化结果,保持输入顺序
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
  /// @return 切片列表,可能为空
  @Override
  public List<PlanSliceAggregate> findByPlanId(Long planId) {
    return mapper.selectList(new QueryWrapper<PlanSliceDO>().eq("plan_id", planId)).stream()
        .map(converter::toAggregate)
        .collect(Collectors.toList());
  }

  /// 根据切片 ID 查找切片。
  ///
  /// @param sliceId 切片 ID
  /// @return 切片聚合根(可选)
  @Override
  public Optional<PlanSliceAggregate> findById(Long sliceId) {
    if (sliceId == null) {
      return Optional.empty();
    }
    PlanSliceDO entity = mapper.selectById(sliceId);
    return Optional.ofNullable(entity).map(converter::toAggregate);
  }
}
