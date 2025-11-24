package com.patra.starter.observability.filter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.common.KeyValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/// 公共标签过滤器。
///
/// 功能：
///
/// - 自动为所有 Observation 添加公共标签（低基数标签）
/// - 添加系统标签：application、environment、region、cluster
/// - 添加用户自定义标签
///
/// 标签来源：
///
/// - application: patra.observability.applicationName
/// - environment: patra.observability.environment
/// - region: patra.observability.region
/// - cluster: patra.observability.cluster
/// - 自定义标签: patra.observability.metrics.commonTags
///
/// 注意：
///
/// - 所有标签添加到 lowCardinalityKeyValues（低基数）
/// - 如果标签值为 null，则跳过该标签
///
/// @author Jobs
/// @since 1.0.0
public class CommonTagsObservationFilter implements ObservationFilter {

    private static final Logger log = LoggerFactory.getLogger(CommonTagsObservationFilter.class);

    private final String applicationName;
    private final String environment;
    private final String region;
    private final String cluster;
    private final Map<String, String> customTags;

    /// 构造函数。
    ///
    /// @param applicationName 应用名称
    /// @param environment 环境标识
    /// @param region 区域标识
    /// @param cluster 集群标识
    /// @param customTags 自定义标签
    public CommonTagsObservationFilter(
        String applicationName,
        String environment,
        String region,
        String cluster,
        Map<String, String> customTags
    ) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.region = region;
        this.cluster = cluster;
        this.customTags = customTags != null ? customTags : Map.of();

        log.info("初始化公共标签过滤器 [应用: {}, 环境: {}, 区域: {}, 集群: {}, 自定义标签数量: {}]",
            applicationName, environment, region, cluster, this.customTags.size());
    }

    /// 为 Observation Context 添加公共标签。
    ///
    /// @param context Observation 上下文
    /// @return 添加了公共标签的上下文
    @Override
    public Observation.Context map(Observation.Context context) {
        // 添加系统标签
        if (applicationName != null && !applicationName.isEmpty()) {
            context.addLowCardinalityKeyValue(KeyValue.of("application", applicationName));
        }

        if (environment != null && !environment.isEmpty()) {
            context.addLowCardinalityKeyValue(KeyValue.of("environment", environment));
        }

        if (region != null && !region.isEmpty()) {
            context.addLowCardinalityKeyValue(KeyValue.of("region", region));
        }

        if (cluster != null && !cluster.isEmpty()) {
            context.addLowCardinalityKeyValue(KeyValue.of("cluster", cluster));
        }

        // 添加自定义标签
        customTags.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                context.addLowCardinalityKeyValue(KeyValue.of(key, value));
            }
        });

        return context;
    }
}
