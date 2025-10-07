package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchResult;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 批次执行器接口。
 * <p>
 * 职责：执行单个批次，调用数据源 API，处理数据并上传到存储。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>策略模式：不同数据源（provenanceCode）可有不同的执行策略。</li>
 *   <li>数据流处理：API 调用 → 数据清洗 → 上传存储 → 返回结果。</li>
 *   <li>异常处理：执行失败时捕获异常，返回 BatchResult.failure()。</li>
 *   <li>游标支持：执行成功后从响应中提取 nextCursorToken。</li>
 *   <li>存储上传：通过 StorageAdapter 上传数据到 OSS，返回 storageKey。</li>
 * </ul>
 * </p>
 * <p>
 * 实现类应注册到 BatchExecutorRegistry，按 provenanceCode 路由。
 * </p>
 *
 * TODO 新增一个pubmed的实现
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface BatchExecutor {

    /**
     * 获取支持的数据源编码。
     *
     * @return 数据源编码（如 "PUBMED", "EPMC"） TODO 改成 common保中的枚举
     */
    String getProvenanceCode();

    /**
     * 执行批次。
     *
     * @param context 执行上下文（包含配置快照、窗口等）
     * @param batch 批次信息（包含查询、参数、游标）
     * @return 批次执行结果
     */
    BatchResult execute(ExecutionContext context, Batch batch);
}
