package com.patra.ingest.domain.messaging;

import java.util.Locale;

/**
 * 消费组命名工具：svc-{service}-{consumer}-cg。
 */
public final class ConsumerGroups {
    private ConsumerGroups() {}

    /**
     * 构造消费组名：小写短横线风格。
     * @param service 微服务名（如 ingest/registry）
     * @param consumer 职责名（如 relay/task-ready）
     */
    public static String svc(String service, String consumer) {
        String s = normalize(service);
        String c = normalize(consumer);
        return "svc-" + s + '-' + c + "-cg";
    }

    private static String normalize(String s) {
        if (s == null) return "unknown";
        return s.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

