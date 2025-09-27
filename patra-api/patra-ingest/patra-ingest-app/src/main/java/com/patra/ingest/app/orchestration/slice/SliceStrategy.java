package com.patra.ingest.app.orchestration.slice;

/**
 * 切片策略枚举，统一维护策略编码，避免散落的硬编码常量。
 *
 * <p>后续若新增策略（例如 ID 驱动、滚动窗口等），在此扩展即可。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum SliceStrategy {

    /** 基于时间窗口按步长拆分。 */
    TIME("TIME"),
    /** 单切片策略，通常用于 UPDATE / ID 驱动。 */
    SINGLE("SINGLE");

    private final String code;

    SliceStrategy(String code) {
        this.code = code;
    }

    /**
     * 返回策略编码，供持久化与 JSON 表达使用。
     */
    public String getCode() {
        return code;
    }

    /**
     * 根据编码解析策略枚举。
     *
     * @param code 策略编码
     * @return 匹配的策略
     * @throws IllegalArgumentException 当编码为空或未匹配时抛出
     */
    public static SliceStrategy fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Slice strategy code cannot be null");
        }
        for (SliceStrategy strategy : values()) {
            if (strategy.code.equalsIgnoreCase(code)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown slice strategy code: " + code);
    }
}
