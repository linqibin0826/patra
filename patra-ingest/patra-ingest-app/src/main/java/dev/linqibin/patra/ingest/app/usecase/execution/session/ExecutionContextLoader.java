package dev.linqibin.patra.ingest.app.usecase.execution.session;

import dev.linqibin.patra.ingest.domain.model.aggregate.TaskAggregate;
import dev.linqibin.patra.ingest.domain.model.vo.execution.ExecutionContext;

/// 执行上下文加载器
///
/// 负责恢复配置和表达式快照(Task → Slice → Plan),校验 Hash,并编译表达式。
///
/// ### 职责
///
/// - **快照恢复**: 从 Task 链路向上追溯 Slice 和 Plan 的配置快照
///   - **Hash 校验**: 验证表达式快照的完整性
///   - **表达式编译**: 将标准化 JSON 编译为可执行的 {@link com.patra.expr.Expr} 对象
///
/// ### 优化策略
///
/// 提供两种加载方式:
///
/// - {@link #loadContext(Long, Long)}: 从 taskId 开始加载(需查询 Task 聚合)
///   - {@link #loadContext(TaskAggregate, Long)}: 复用已加载的 Task 聚合,避免重复查询
///
/// @author linqibin
/// @since 0.1.0
public interface ExecutionContextLoader {

  /// 加载执行上下文(配置恢复 + 表达式编译)
  ///
  /// @param taskId 任务 ID
  /// @param runId 运行 ID
  /// @return 执行上下文
  ExecutionContext loadContext(Long taskId, Long runId);

  /// 加载执行上下文(配置恢复 + 表达式编译) - 优化版,避免重复加载 Task
  ///
  /// @param task 任务聚合(已加载)
  /// @param runId 运行 ID
  /// @return 执行上下文
  ExecutionContext loadContext(TaskAggregate task, Long runId);
}
