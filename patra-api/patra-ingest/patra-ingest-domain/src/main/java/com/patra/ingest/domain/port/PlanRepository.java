package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.Plan;

import java.util.List;
import java.util.Optional;

/**
 * 计划蓝图仓储端口。
 *
 * 提供基础的按标识/业务键查询与保存能力。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

    /** 按ID查询 */
    Optional<Plan> findById(Long id);

    /** 按业务键查询 */
    Optional<Plan> findByPlanKey(String planKey);

    /** 按调度实例ID查询全部计划 */
    List<Plan> findByScheduleInstanceId(Long scheduleInstanceId);

    /** 保存计划（新建或更新） */
    Plan save(Plan plan);
}

