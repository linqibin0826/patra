package com.patra.starter.rocketmq.support;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 基于 Spring Profile 推导命名空间（namespace）。
 *
 * 规则：
 * - 若配置文件已显式指定 naming.namespace，则优先使用该值；
 * - 否则读取第一个激活的 profile（无激活则取默认 profile），转换为大写并去除非 [A-Z0-9] 字符；
 * - 若最终为空，回退为 DEV。
 */
public final class EnvNamespaceResolver {

    private EnvNamespaceResolver() {
    }

    public static String resolve(Environment env, PatraRocketMQProperties.Naming naming) {
        String ns = naming != null ? naming.getNamespace() : null;
        if (!StringUtils.hasText(ns)) {
            String[] actives = env != null ? env.getActiveProfiles() : new String[0];
            String candidate = (actives != null && actives.length > 0)
                    ? actives[0]
                    : firstOrDefault(env != null ? env.getDefaultProfiles() : new String[0], "dev");
            ns = sanitize(candidate);
        } else {
            ns = sanitize(ns);
        }
        if (!StringUtils.hasText(ns)) {
            ns = "DEV";
        }
        return ns;
    }

    private static String firstOrDefault(String[] arr, String def) {
        return (arr != null && arr.length > 0) ? arr[0] : def;
    }

    /**
     * 仅保留大写字母数字，剔除其他符号，避免破坏 Topic 命名正则。
     */
    private static String sanitize(String s) {
        if (!StringUtils.hasText(s)) {
            return s;
        }
        String up = s.toUpperCase(Locale.ROOT);
        return up.replaceAll("[^A-Z0-9]", "");
    }
}

