package com.patra.starter.rocketmq.naming;

import org.springframework.core.env.Environment;

import java.util.Locale;

/**
 * 命名空间解析器：从环境推导 namespace。
 *
 * <p>规则：
 * <ul>
 *   <li>优先使用配置的 namespace</li>
 *   <li>否则从 spring.profiles.active 第一个值推导</li>
 *   <li>转大写并去除非字母数字字符</li>
 *   <li>默认回退为 DEV</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class NamespaceResolver {

    /**
     * 解析命名空间。
     */
    public static String resolve(Environment environment, String configuredNamespace) {
        if (configuredNamespace != null && !configuredNamespace.isBlank()) {
            return sanitize(configuredNamespace);
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return sanitize(activeProfiles[0]);
        }

        String[] defaultProfiles = environment.getDefaultProfiles();
        if (defaultProfiles.length > 0) {
            return sanitize(defaultProfiles[0]);
        }

        return "DEV";
    }

    /**
     * 清理命名空间：仅保留大写字母和数字。
     */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "DEV";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        String cleaned = upper.replaceAll("[^A-Z0-9]", "");
        return cleaned.isEmpty() ? "DEV" : cleaned;
    }
}
