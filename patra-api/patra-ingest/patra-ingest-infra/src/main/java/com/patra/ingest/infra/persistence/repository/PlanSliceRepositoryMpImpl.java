package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.persistence.converter.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 计划切片（PlanSliceAggregate）仓储实现（MyBatis-Plus）。
 * <p>职责：
 * <ul>
 *   <li>切片聚合的插入 / 更新（通过是否存在 ID 判断）。</li>
 *   <li>批量保存（顺序调用，保持输入顺序）。</li>
 *   <li>按 planId 查询全部切片（常用于调度/回放）。</li>
 * </ul>
 * </p>
 * <p>设计与约束：
 * <ul>
 *   <li>不在仓储层做复杂状态机校验；状态转换由应用服务控制。</li>
 *   <li>乐观锁：如 DO 含 version，更新由 MP 自动处理；需根据未来并发场景可扩展条件更新。</li>
 *   <li>幂等：由上层保证不重复创建同一业务语义切片（如 sliceSignatureHash）。</li>
 * </ul>
 * </p>
 * <p>日志策略：
 * <ul>
 *   <li>DEBUG：insert / update 打印 planId + hash（exprHash 代表表达式指纹）。</li>
 *   <li>无 INFO：高频路径避免噪声；错误交由上层捕获统一处理。</li>
 * </ul>
 * </p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PlanSliceRepositoryMpImpl implements PlanSliceRepository {

    private final PlanSliceMapper mapper;
    private final PlanSliceConverter converter;

    /**
     * 保存单个切片（insert or update）。
     * @param slice 切片聚合
     * @return 持久化后的聚合（重新映射，确保包含生成字段）
     */
    @Override
    public PlanSliceAggregate save(PlanSliceAggregate slice) {
        PlanSliceDO entity = converter.toEntity(slice);
        if (entity.getId() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] slice insert planId={} hash={}", entity.getPlanId(), entity.getExprHash());
            }
            mapper.insert(entity);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] slice update id={} planId={} hash={}", entity.getId(), entity.getPlanId(), entity.getExprHash());
            }
            mapper.updateById(entity);
        }
        return converter.toAggregate(entity);
    }

    /**
     * 批量保存切片（内部逐条调用 {@link #save(PlanSliceAggregate)}）。
     * @param slices 切片集合
     * @return 持久化后结果（与输入顺序保持一致）
     */
    @Override
    public List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices) {
        List<PlanSliceAggregate> persisted = new ArrayList<>(slices.size());
        for (PlanSliceAggregate slice : slices) {
            persisted.add(save(slice));
        }
        return persisted;
    }

    /**
     * 按计划 ID 查询切片集合。
     * @param planId 计划 ID
     * @return 切片列表（可能为空）
     */
    @Override
    public List<PlanSliceAggregate> findByPlanId(Long planId) {
        return mapper.selectList(new QueryWrapper<PlanSliceDO>().eq("plan_id", planId))
                .stream()
                .map(converter::toAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PlanSliceAggregate> findById(Long sliceId) {
        if (sliceId == null) {
            return Optional.empty();
        }
        PlanSliceDO entity = mapper.selectById(sliceId);
        return Optional.ofNullable(entity).map(converter::toAggregate);
    }
}
