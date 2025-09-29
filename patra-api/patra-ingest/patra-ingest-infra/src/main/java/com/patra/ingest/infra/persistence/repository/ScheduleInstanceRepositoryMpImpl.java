package com.patra.ingest.infra.persistence.repository;

import cn.hutool.core.lang.Assert;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.infra.persistence.converter.ScheduleInstanceConverter;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 调度实例（ScheduleInstanceAggregate）仓储实现。
 * <p>职责：
 * <ul>
 *   <li>基于是否存在 ID 决定 insert / update。</li>
 *   <li>不校验调度业务语义（如触发类型合法性），交由领域校验。</li>
 *   <li>幂等：由上层保证不重复创建同一实例含义记录。</li>
 * </ul>
 * </p>
 * <p>日志策略：DEBUG 输出 insert/update 关键字段；不打印 INFO。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryMpImpl implements ScheduleInstanceRepository {

    private final ScheduleInstanceMapper mapper;
    private final ScheduleInstanceConverter converter;

    /**
     * 保存或更新调度实例。
     * @param instance 调度实例聚合
     * @return 持久化后聚合（若为 insert 则使用转换后的新实例）
     */
    @Override
    public ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance) {
        Assert.notNull(instance, "ScheduleInstanceAggregate cannot be null");

        ScheduleInstanceDO entity = converter.toDO(instance);
        if (instance.getId() != null) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] schedule instance update id={} triggerType={}", instance.getId(), instance.getTriggerType());
            }
            mapper.updateById(entity);
            return instance;
        }
        mapper.insert(entity);
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] schedule instance insert triggeredAt={} id={} (post-insert)", entity.getTriggeredAt(), entity.getId());
        }
        return converter.toDomain(entity);
    }
}
