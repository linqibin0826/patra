package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 执行上下文加载器。
 * <p>从Task→Slice→Plan还原配置快照与表达式快照，校验哈希，编译表达式。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecutionContextLoader {

    /**
     * 加载执行上下文（配置还原 + 表达式编译）。
     *
     * @param taskId 任务ID
     * @param runId 运行ID
     * @return 执行上下文
     */
    ExecutionContext loadContext(Long taskId, Long runId);
}
