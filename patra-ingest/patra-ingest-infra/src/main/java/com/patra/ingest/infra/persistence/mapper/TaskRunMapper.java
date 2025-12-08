package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/// 任务执行记录 Mapper 接口 — 对任务执行记录表的数据访问操作。
///
/// 职责:
///
/// - 持久化单个任务执行尝试(开始/结束/状态/指标)
///   - 查询最新 attemptNo(用于推导下一次尝试编号)
///   - 未来: 可根据需要添加按任务/批次/状态查询的方法
///
/// 保持此 Mapper 专注;避免在此处编写跨表聚合逻辑。
///
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface TaskRunMapper extends BaseMapper<TaskRunDO> {

  /// 获取任务的最新尝试编号。
  ///
  /// 实现位置: TaskRunMapper.xml#selectLatestAttemptNo
  ///
  /// @param taskId 任务 ID
  /// @return 最大 attemptNo,无记录时返回 0
  int selectLatestAttemptNo(@Param("taskId") Long taskId);

  /// 覆盖检查点并刷新心跳时间戳
  int updateCheckpointAndHeartbeat(
      @Param("runId") Long runId,
      @Param("checkpointJson") String checkpointJson,
      @Param("now") Instant now);

  /// 刷新心跳时间戳
  int touchHeartbeat(@Param("runId") Long runId, @Param("now") Instant now);

  /// 标记运行记录为失败
  int markFailed(
      @Param("runId") Long runId, @Param("errorMsg") String errorMsg, @Param("now") Instant now);
}
