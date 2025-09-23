package com.patra.ingest.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.app.port.outbound.TaskInventoryPort;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskInventoryPortImpl implements TaskInventoryPort {

    private final TaskMapper taskMapper;

    @Override
    public long countQueuedTasks(String provenanceCode, String operationCode) {
        QueryWrapper<TaskDO> wrapper = new QueryWrapper<>();
        wrapper.eq("status_code", "QUEUED");
        if (provenanceCode != null) {
            wrapper.eq("provenance_code", provenanceCode);
        }
        if (operationCode != null) {
            wrapper.eq("operation_code", operationCode);
        }
        return taskMapper.selectCount(wrapper);
    }
}
