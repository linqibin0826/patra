package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务执行（TaskRun）表 Mapper。
 * <p>
 * 主要用于：
 * <ul>
 *   <li>记录单次任务运行（含开始 / 结束 / 状态 / 统计指标）。</li>
 *   <li>后续可扩展根据任务 / 批次 / 状态的查询。</li>
 * </ul>
 * 不在此处加入跨表统计逻辑，保持 Mapper 纯粹。
 * </p>
 */
@Mapper
public interface TaskRunMapper extends BaseMapper<TaskRunDO> {
}
