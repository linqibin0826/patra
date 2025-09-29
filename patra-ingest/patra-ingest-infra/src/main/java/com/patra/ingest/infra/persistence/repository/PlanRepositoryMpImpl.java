package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 计划仓储 MyBatis-Plus 实现（Infra Layer）。
 * <p>
 * 职责：
 * <ul>
 *   <li>PlanAggregate ↔ PlanDO 映射</li>
 *   <li>按 planKey 幂等查询 / 存在性判断</li>
 *   <li>插入 / 更新（无复杂条件更新，乐观锁交由 MP 版本字段维护）</li>
 * </ul>
 * </p>
 * 日志策略：
 * <ul>
 *   <li>DEBUG：insert / update 关键字段（id, planKey）</li>
 *   <li>INFO：避免高频 CRUD 噪声，不打印</li>
 * </ul>
 * 线程安全：无状态，依赖注入单例。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    /** 计划 Mapper */
    private final PlanMapper planMapper;
    /** 聚合与 DO 转换器 */
    private final PlanConverter planConverter;

    /**
     * 保存 Plan：根据是否有 ID 决定 insert 或 update。
     * <p>聚合到 DO 再回转，保证版本 / 自增主键回写。</p>
     * @param plan 聚合（必填）
     * @return 持久化后聚合
     */
    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO entity = planConverter.toEntity(plan);
        if (entity.getId() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][REPO] plan insert planKey={}", entity.getPlanKey());
            }
            planMapper.insert(entity);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][REPO] plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
            }
            planMapper.updateById(entity);
        }
        return planConverter.toAggregate(entity);
    }

    /**
     * 按 planKey 查询。
     * @param planKey 幂等键（为空返回 empty）
     */
    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return Optional.empty();
        }
        PlanDO entity = planMapper.findByPlanKey(planKey);
        return Optional.ofNullable(entity).map(planConverter::toAggregate);
    }

    /**
     * 判断 planKey 是否存在。
     * @param planKey 幂等键
     */
    @Override
    public boolean existsByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return false;
        }
        return planMapper.countByPlanKey(planKey) > 0;
    }
}
