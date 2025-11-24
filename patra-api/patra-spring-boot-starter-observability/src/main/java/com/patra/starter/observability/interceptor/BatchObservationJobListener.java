package com.patra.starter.observability.interceptor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 批处理任务可观测性监听器。
 *
 * <p>功能：
 * <ul>
 *   <li>为批处理任务创建 Observation</li>
 *   <li>自动记录任务名称、状态、执行时间等关键信息</li>
 *   <li>与 Micrometer Observation 集成，自动生成追踪和指标</li>
 * </ul>
 *
 * <p>实现模式：
 * <ul>
 *   <li>在 beforeJob 阶段创建并启动 Observation</li>
 *   <li>将 Observation 存储在内存映射中（通过 JobExecution ID 关联）</li>
 *   <li>在 afterJob 阶段停止 Observation 并记录结果</li>
 *   <li>清理已完成任务的 Observation</li>
 * </ul>
 *
 * <p>Observation 标签：
 * <ul>
 *   <li>job.name - 任务名称</li>
 *   <li>job.execution.id - 任务执行 ID</li>
 *   <li>job.status - 任务最终状态（COMPLETED、FAILED、STOPPED 等）</li>
 *   <li>job.exit.code - 任务退出码</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>监控批处理任务的执行时间和成功率</li>
 *   <li>自动集成分布式追踪（跨任务步骤追踪）</li>
 *   <li>生成批处理任务指标（通过 DefaultMeterObservationHandler）</li>
 * </ul>
 *
 * <p>注意事项：
 * <ul>
 *   <li>使用 ConcurrentHashMap 存储 Observation，支持并发任务</li>
 *   <li>任务完成后自动清理 Observation，防止内存泄漏</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
public class BatchObservationJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchObservationJobListener.class);

    private static final String OBSERVATION_NAME = "batch.job.execution";

    private final ObservationRegistry observationRegistry;

    /**
     * 存储正在执行的任务的 Observation（Key: JobExecution ID）。
     */
    private final ConcurrentMap<Long, Observation> observationMap = new ConcurrentHashMap<>();

    /**
     * 构造函数。
     *
     * @param observationRegistry Observation 注册中心
     */
    public BatchObservationJobListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
        log.info("初始化批处理任务可观测性监听器");
    }

    /**
     * 任务开始前回调。
     *
     * <p>操作：
     * <ol>
     *   <li>创建 Observation 并添加任务信息标签</li>
     *   <li>启动 Observation</li>
     *   <li>存储 Observation 到内存映射</li>
     * </ol>
     *
     * @param jobExecution 任务执行对象
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long executionId = jobExecution.getId();

        // 创建 Observation
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry);

        // 添加低基数标签（任务信息）
        observation.lowCardinalityKeyValue("job.name", jobName);
        observation.lowCardinalityKeyValue("job.execution.id", String.valueOf(executionId));

        // 启动 Observation
        observation.start();

        // 存储 Observation
        observationMap.put(executionId, observation);

        log.debug("批处理任务开始: {} (执行 ID: {})", jobName, executionId);
    }

    /**
     * 任务完成后回调。
     *
     * <p>操作：
     * <ol>
     *   <li>从内存映射中获取 Observation</li>
     *   <li>添加任务状态和退出码标签</li>
     *   <li>如果任务失败，记录错误事件</li>
     *   <li>停止 Observation</li>
     *   <li>清理内存映射</li>
     * </ol>
     *
     * @param jobExecution 任务执行对象
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        Long executionId = jobExecution.getId();
        BatchStatus status = jobExecution.getStatus();

        // 从映射中获取 Observation
        Observation observation = observationMap.remove(executionId);

        if (observation == null) {
            log.warn("未找到任务 {} (执行 ID: {}) 的 Observation", jobName, executionId);
            return;
        }

        try {
            // 添加任务结果标签
            observation.lowCardinalityKeyValue("job.status", status.name());
            observation.lowCardinalityKeyValue("job.exit.code", jobExecution.getExitStatus().getExitCode());

            // 如果任务失败，记录错误事件
            if (status == BatchStatus.FAILED) {
                // 收集所有异常信息
                jobExecution.getAllFailureExceptions().forEach(observation::error);

                log.error("批处理任务失败: {} (执行 ID: {}), 状态: {}", jobName, executionId, status);
            } else {
                log.debug("批处理任务完成: {} (执行 ID: {}), 状态: {}", jobName, executionId, status);
            }

        } finally {
            // 停止 Observation（无论成功或失败）
            observation.stop();
        }
    }

    /**
     * 获取当前活跃的 Observation 数量。
     *
     * <p>用于监控和调试，检查是否存在内存泄漏。
     *
     * @return 活跃 Observation 数量
     */
    public int getActiveObservationCount() {
        return observationMap.size();
    }
}
