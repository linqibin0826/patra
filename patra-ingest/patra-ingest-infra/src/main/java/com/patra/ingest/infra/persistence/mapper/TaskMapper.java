package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/// 任务 Mapper 接口 — 对任务表的数据访问操作。
///
/// 职责:
///
/// - 继承 MyBatis-Plus {@link BaseMapper} 为任务表提供通用 CRUD
///   - 提供租约相关的 SQL 操作(CAS 获取、续约、标记 RUNNING)
///   - 此处无业务语义;领域/应用层强制执行业务规则
///   - 如需复杂查询,优先组合仓储操作;需要自定义 SQL 时,保持方法名清晰、添加完整 Javadoc 并记录对应 XML(如有)。避免跨聚合 JOIN
///
/// 线程安全性: 无状态接口;MyBatis 生成的代理是单例且并发重用安全。
///
/// 日志: 避免在 Mapper 层记录日志;让仓储层记录关键路径以减少 I/O 噪声。
///
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {

  /// CAS 租约获取(步骤 0)。
  ///
  /// 仅更新满足调度和租约接管条件的 QUEUED 状态任务。实现位置: TaskMapper.xml#tryAcquireLease
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者 ID
  /// @param now 当前时间(UTC)
  /// @param ttlSec 租约 TTL(秒)
  /// @param idem 幂等键(防御性检查)
  /// @return 受影响行数(1=成功,0=失败)
  int tryAcquireLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("ttlSec") int ttlSec,
      @Param("idem") String idem);

  /// 标记任务为 RUNNING 并更新租约(步骤 1)。
  ///
  /// 前置条件: WHERE lease_owner=#{owner};实现位置: TaskMapper.xml#markRunningWithLease
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param ttlSec 租约 TTL(秒)
  /// @return 受影响行数(1=成功,0=租约丢失)
  int markRunningWithLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("ttlSec") int ttlSec);

  /// 心跳租约续约。
  ///
  /// 前置条件: WHERE lease_owner=#{owner};实现位置: TaskMapper.xml#renewLease
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param ttlSec 租约 TTL(秒)
  /// @return 受影响行数(1=成功,0=租约丢失)
  int renewLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("ttlSec") int ttlSec);

  /// 批量心跳租约续约(性能优化)。
  ///
  /// 前置条件: WHERE id IN (taskIds) AND lease_owner=#{owner};实现位置: TaskMapper.xml#batchRenewLeases
  ///
  /// @param taskIds 任务 ID 列表
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param ttlSec 租约 TTL(秒)
  /// @return 受影响行数(已续约的任务数)
  int batchRenewLeases(
      @Param("taskIds") java.util.List<Long> taskIds,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("ttlSec") int ttlSec);
}
