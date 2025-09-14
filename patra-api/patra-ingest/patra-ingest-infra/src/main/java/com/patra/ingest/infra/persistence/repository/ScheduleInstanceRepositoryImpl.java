package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.domain.model.enums.SchedulerSource;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.infra.mapstruct.ScheduleInstanceConverter;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.IngScheduleInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 调度实例仓储实现（infra）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryImpl implements ScheduleInstanceRepository {

    private final IngScheduleInstanceMapper mapper;
    private final ScheduleInstanceConverter converter;

    @Override
    public Optional<ScheduleInstance> findById(Long id) {
        ScheduleInstanceDO obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public Optional<ScheduleInstance> findBySchedulerTuple(SchedulerSource scheduler, String schedulerJobId, String schedulerLogId) {
        var q = new LambdaQueryWrapper<ScheduleInstanceDO>()
                .eq(ScheduleInstanceDO::getScheduler, scheduler)
                .eq(ScheduleInstanceDO::getSchedulerJobId, schedulerJobId)
                .last("limit 1");
        ScheduleInstanceDO obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public ScheduleInstance save(ScheduleInstance aggregate) {
        ScheduleInstanceDO toSave = converter.toDO(aggregate);
        if (toSave.getId() == null) {
            mapper.insert(toSave);
        } else {
            mapper.updateById(toSave);
        }
        return converter.toAggregate(toSave);
    }
}

