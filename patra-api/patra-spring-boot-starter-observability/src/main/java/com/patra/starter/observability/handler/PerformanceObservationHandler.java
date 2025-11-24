package com.patra.starter.observability.handler;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能观测处理器。
 *
 * <p>功能：
 * <ul>
 *   <li>记录 Observation 的执行时间</li>
 *   <li>检测慢操作并记录警告日志</li>
 *   <li>收集性能统计信息</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
public class PerformanceObservationHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(PerformanceObservationHandler.class);

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final Duration slowThreshold;

    /**
     * 构造函数。
     *
     * @param slowThreshold 慢操作阈值
     */
    public PerformanceObservationHandler(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
        log.info("初始化性能观测处理器，慢操作阈值: {}ms", slowThreshold.toMillis());
    }

    /**
     * 判断是否支持该 Context。
     *
     * @param context Observation 上下文
     * @return true 表示支持所有 Context
     */
    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    /**
     * Observation 启动时的处理。
     *
     * @param context Observation 上下文
     */
    @Override
    public void onStart(Observation.Context context) {
        String key = getKey(context);
        startTimes.put(key, System.currentTimeMillis());
        log.trace("观测开始: {}", context.getName());
    }

    /**
     * Observation 停止时的处理。
     *
     * @param context Observation 上下文
     */
    @Override
    public void onStop(Observation.Context context) {
        String key = getKey(context);
        Long startTime = startTimes.remove(key);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > slowThreshold.toMillis()) {
                log.warn("慢操作检测: {} 耗时 {}ms，超过阈值 {}ms",
                    context.getName(), duration, slowThreshold.toMillis());
            } else {
                log.debug("观测完成: {} 耗时 {}ms", context.getName(), duration);
            }
        }
    }

    /**
     * Observation 发生错误时的处理。
     *
     * @param context Observation 上下文
     */
    @Override
    public void onError(Observation.Context context) {
        // 清理 startTimes，防止内存泄漏
        String key = getKey(context);
        Long startTime = startTimes.remove(key);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("观测错误: {}, 耗时 {}ms, 错误: {}",
                context.getName(),
                duration,
                context.getError() != null ? context.getError().getMessage() : "Unknown");
        } else {
            log.error("观测错误: {}, 错误: {}",
                context.getName(),
                context.getError() != null ? context.getError().getMessage() : "Unknown");
        }
    }

    /**
     * 生成唯一 Key。
     *
     * <p>使用 Observation 名称和线程 ID 组合，确保并发场景下的唯一性。
     *
     * @param context Observation 上下文
     * @return 唯一 Key
     */
    private String getKey(Observation.Context context) {
        return context.getName() + "-" + Thread.currentThread().getId();
    }
}
