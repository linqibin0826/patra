package com.patra.starter.redisson.listener;

/// 锁键模式提取工具类。
///
/// 从锁键中提取低基数模式，用于指标标签，避免指标爆炸。
///
/// **提取规则**：
/// - 移除常见前缀（如 `patra:lock:`、`catalog:lock:` 等）
/// - 去除动态部分（纯数字、UUID、日期格式）
/// - 保留静态业务标识符
///
/// **示例**：
/// - `patra:lock:user:123` → `user`
/// - `patra:lock:order:456:item:789` → `order.item`
/// - `catalog:lock:mesh-import:2024` → `mesh-import`
///
/// @author Patra Team
/// @since 1.0.0
public final class LockKeyPatternExtractor {

    private LockKeyPatternExtractor() {
        // 工具类，禁止实例化
    }

    /// 从锁键中提取模式（去除动态部分）。
    ///
    /// @param lockKey 完整锁键
    /// @return 锁键模式（低基数）
    public static String extract(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            return "unknown";
        }

        // 移除常见前缀（如 "patra:lock:", "catalog:lock:" 等）
        String pattern = lockKey.replaceFirst("^[a-z-]+:lock:", "");

        // 分割并提取非数字部分作为模式
        String[] parts = pattern.split(":");
        StringBuilder patternBuilder = new StringBuilder();

        for (String part : parts) {
            // 跳过纯数字、UUID、日期等动态值
            if (isStaticPart(part)) {
                if (!patternBuilder.isEmpty()) {
                    patternBuilder.append(".");
                }
                patternBuilder.append(part);
            }
        }

        return patternBuilder.isEmpty() ? "unknown" : patternBuilder.toString();
    }

    /// 判断是否为静态部分（非动态值）。
    ///
    /// @param part 键的一部分
    /// @return true 如果是静态部分
    private static boolean isStaticPart(String part) {
        if (part == null || part.isEmpty()) {
            return false;
        }
        // 纯数字
        if (part.matches("^\\d+$")) {
            return false;
        }
        // UUID 格式
        if (part.matches("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$")) {
            return false;
        }
        // 日期格式（如 2024-01-01, 20240101）
        if (part.matches("^\\d{4}(-\\d{2}){0,2}$") || part.matches("^\\d{8}$")) {
            return false;
        }
        return true;
    }
}
