package com.patra.ingest.app.usecase.execution.execute;

import com.patra.common.enums.ProvenanceCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批次执行器注册表。
 * <p>
 * 职责：管理所有 BatchExecutor 实例，按 provenanceCode 路由到对应的执行器。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>自动注册：通过 Spring 构造函数注入所有 BatchExecutor 实例。</li>
 *   <li>线程安全：使用 ConcurrentHashMap 存储映射关系。</li>
 *   <li>异常处理：找不到执行器时抛出 IllegalArgumentException。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
@Slf4j
public class BatchExecutorRegistry {

    private final Map<String, BatchExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 构造函数：自动注册所有 BatchExecutor 实例。
     *
     * @param executorList Spring 注入的所有 BatchExecutor 实例
     */
    public BatchExecutorRegistry(List<BatchExecutor> executorList) {
        for (BatchExecutor executor : executorList) {
            ProvenanceCode provenanceCode = executor.getProvenanceCode();
            String code = provenanceCode.getCode();
            if (executors.containsKey(code)) {
                log.warn("[INGEST][APP] duplicate batch executor for provenanceCode={}", code);
            }
            executors.put(code, executor);
            log.info("[INGEST][APP] registered batch executor provenanceCode={} class={}",
                     code, executor.getClass().getSimpleName());
        }
    }

    /**
     * 根据数据源编码获取批次执行器。
     *
     * @param provenanceCode 数据源编码
     * @return 批次执行器
     * @throws IllegalArgumentException 找不到执行器时抛出
     */
    public BatchExecutor get(String provenanceCode) {
        BatchExecutor executor = executors.get(provenanceCode);
        if (executor == null) {
            throw new IllegalArgumentException(
                "未找到批次执行器 provenanceCode=" + provenanceCode
                + " 可用执行器: " + executors.keySet()
            );
        }
        return executor;
    }

    /**
     * 检查是否存在指定数据源的执行器。
     */
    public boolean contains(String provenanceCode) {
        return executors.containsKey(provenanceCode);
    }
}
