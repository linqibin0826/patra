package com.patra.starter.observability.filter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 高基数标签过滤器。
///
/// 功能：
///
/// - 过滤高基数标签（userId、requestId、traceId 等）
/// - 防止时序数据库性能问题
/// - 支持自定义高基数标签黑名单
///
/// 高基数标签的危害：
///
/// - 值的可能性非常多（如 userId 可能有数百万个不同值）
/// - 导致时序数据库创建大量唯一的时间序列
/// - 严重影响查询性能和存储成本
///
/// 默认高基数标签黑名单：
///
/// - userId、user_id
/// - requestId、request_id
/// - traceId、trace_id
/// - spanId、span_id
/// - sessionId、session_id
/// - timestamp
/// - uuid
/// - ip
/// - email
/// - phone
///
/// 使用场景：
///
/// - 保护时序数据库：防止高基数标签导致的性能问题
/// - 生产环境必备：确保指标系统的稳定性
/// - 开发规范：强制开发者使用低基数标签
///
/// @author Jobs
/// @since 1.0.0
public class HighCardinalityMeterFilter implements MeterFilter {

    private static final Logger log = LoggerFactory.getLogger(HighCardinalityMeterFilter.class);

    /// 默认高基数标签黑名单。
    private static final Set<String> DEFAULT_HIGH_CARDINALITY_KEYS = Set.of(
        "userId", "user_id",
        "requestId", "request_id",
        "traceId", "trace_id",
        "spanId", "span_id",
        "sessionId", "session_id",
        "timestamp",
        "uuid",
        "ip",
        "email",
        "phone"
    );

    private final Set<String> highCardinalityKeys;

    /// 构造函数（使用默认黑名单）。
    public HighCardinalityMeterFilter() {
        this(null);
    }

    /// 构造函数。
    ///
    /// @param customHighCardinalityKeys 用户自定义高基数标签黑名单（null 表示仅使用默认黑名单）
    public HighCardinalityMeterFilter(Set<String> customHighCardinalityKeys) {
        this.highCardinalityKeys = new HashSet<>(DEFAULT_HIGH_CARDINALITY_KEYS);

        // 合并用户自定义黑名单
        if (customHighCardinalityKeys != null && !customHighCardinalityKeys.isEmpty()) {
            this.highCardinalityKeys.addAll(customHighCardinalityKeys);
        }

        log.info("初始化高基数标签过滤器，黑名单标签数量: {}, 黑名单: {}",
            this.highCardinalityKeys.size(), this.highCardinalityKeys);
    }

    /// 过滤高基数标签。
    ///
    /// @param id Meter ID
    /// @return 移除高基数标签后的 Meter ID
    @Override
    public Meter.Id map(Meter.Id id) {
        List<Tag> originalTags = id.getTags();
        List<Tag> filteredTags = new ArrayList<>();
        List<String> removedKeys = new ArrayList<>();

        // 遍历所有标签，移除高基数标签
        for (Tag tag : originalTags) {
            if (highCardinalityKeys.contains(tag.getKey())) {
                removedKeys.add(tag.getKey());
            } else {
                filteredTags.add(tag);
            }
        }

        // 如果有标签被移除，记录日志并返回新的 Meter.Id
        if (!removedKeys.isEmpty()) {
            log.debug("指标 [{}] 移除高基数标签: {}", id.getName(), removedKeys);

            // 重新构建 Meter.Id（保留原始名称、类型、基础单位、描述）
            return new Meter.Id(
                id.getName(),
                Tags.of(filteredTags),
                id.getBaseUnit(),
                id.getDescription(),
                id.getType()
            );
        }

        return id;
    }
}
