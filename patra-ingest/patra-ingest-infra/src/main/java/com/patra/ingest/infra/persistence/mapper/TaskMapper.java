package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * 任务表 Mapper 接口。
 * <p>
 * 职责：
 * <ul>
 *   <li>继承 MyBatis-Plus {@link BaseMapper}，提供对任务数据表的通用 CRUD 能力。</li>
 *   <li>扩展租约相关的复杂 SQL 方法（CAS 抢占、续租、置 RUNNING）。</li>
 *   <li>不在此层书写业务语义，所有领域规则在领域 / 应用层实现。</li>
 *   <li>如需添加复杂查询，请：
 *     <ol>
 *       <li>优先考虑是否可在仓储实现中组合通用方法达成；</li>
 *       <li>确需自定义 SQL 时，保持方法命名清晰、添加完整 Javadoc，并在对应 XML（若存在）增加注释；</li>
 *       <li>注意避免跨聚合的多表 join，违背六边形架构边界。</li>
 *     </ol>
 *   </li>
 * </ul>
 * </p>
 * <p>
 * 线程安全：接口本身无状态；由 MyBatis 生成的代理在 Spring 容器中为单例，可并发安全复用。
 * </p>
 * <p>
 * 日志策略：Mapper 层不直接打日志，统一由上层仓储实现（Repository）在关键路径输出，以避免高频 I/O 噪声。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {

    /**
     * CAS 抢占租约（步骤 0）。
     * <p>
     * 仅针对 QUEUED 状态且满足调度时间、租约可接管条件的任务进行 CAS 更新。
     * 实现位于：TaskMapper.xml#tryAcquireLease
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者标识
     * @param now 当前时间（UTC）
     * @param ttlSec 租约 TTL（秒）
     * @param idem 幂等键（防御性校验）
     * @return 受影响行数（1=成功，0=失败）
     */
    int tryAcquireLease(@Param("taskId") Long taskId,
                        @Param("owner") String owner,
                        @Param("now") Instant now,
                        @Param("ttlSec") int ttlSec,
                        @Param("idem") String idem);

    /**
     * 置任务为 RUNNING 状态并更新租约（步骤 1）。
     * <p>
     * 前置条件：WHERE lease_owner=#{owner}（防止被窃取）
     * 实现位于：TaskMapper.xml#markRunningWithLease
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者
     * @param now 当前时间
     * @param ttlSec 租约 TTL（秒）
     * @return 受影响行数（1=成功，0=租约丢失）
     */
    int markRunningWithLease(@Param("taskId") Long taskId,
                             @Param("owner") String owner,
                             @Param("now") Instant now,
                             @Param("ttlSec") int ttlSec);

    /**
     * 心跳续租。
     * <p>
     * 前置条件：WHERE lease_owner=#{owner}
     * 实现位于：TaskMapper.xml#renewLease
     * </p>
     *
     * @param taskId 任务 ID
     * @param owner 租约持有者
     * @param now 当前时间
     * @param ttlSec 租约 TTL（秒）
     * @return 受影响行数（1=成功，0=租约丢失）
     */
    int renewLease(@Param("taskId") Long taskId,
                   @Param("owner") String owner,
                   @Param("now") Instant now,
                   @Param("ttlSec") int ttlSec);
}
