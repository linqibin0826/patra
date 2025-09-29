package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务表 Mapper 接口。
 * <p>
 * 职责：
 * <ul>
 *   <li>继承 MyBatis-Plus {@link BaseMapper}，提供对任务数据表的通用 CRUD 能力。</li>
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
	// 当前仅使用 BaseMapper 默认方法；后续扩展时请补充方法级 Javadoc。
}
