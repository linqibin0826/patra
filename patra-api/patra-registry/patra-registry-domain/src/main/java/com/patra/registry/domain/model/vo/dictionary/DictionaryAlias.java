package com.patra.registry.domain.model.vo.dictionary;

/**
 * 外部系统集成用的字典别名值对象（不可变）。
 *
 * <p>描述外部系统编码与内部字典项之间的映射，便于对接遗留系统与外部数据源。</p>
 *
 * @param sourceSystem 外部系统标识（如 "LEGACY_ERP", "EXTERNAL_API"）
 * @param externalCode 外部系统的编码
 * @param externalLabel 外部系统的人类可读标签
 * @param notes 该映射的附注、转换规则或使用场景
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryAlias(
    String sourceSystem,
    String externalCode,
    String externalLabel,
    String notes
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryAlias {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }
        // Normalize the values to ensure consistency
        sourceSystem = sourceSystem.trim().toUpperCase();
        externalCode = externalCode.trim();
        externalLabel = externalLabel != null ? externalLabel.trim() : "";
        notes = notes != null ? notes.trim() : "";
    }
    
    /** 是否具有有效的外部标签。 */
    public boolean hasExternalLabel() {
        return externalLabel != null && !externalLabel.isEmpty();
    }
    
    /** 是否包含附注说明。 */
    public boolean hasNotes() {
        return notes != null && !notes.isEmpty();
    }
    
    /** 获取用于展示的标签（优先外部标签，否则使用外部编码）。 */
    public String getDisplayLabel() {
        return hasExternalLabel() ? externalLabel : externalCode;
    }
    
    /** 创建别名唯一键（sourceSystem:externalCode）。 */
    public String getUniqueKey() {
        return sourceSystem + ":" + externalCode;
    }
}
