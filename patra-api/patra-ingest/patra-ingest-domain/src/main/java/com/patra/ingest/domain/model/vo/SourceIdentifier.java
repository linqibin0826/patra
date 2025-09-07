package com.patra.ingest.domain.model.vo;

import lombok.Value;

/**
 * 数据源标识值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class SourceIdentifier {
    
    /**
     * 源系统标识
     */
    String sourceSystem;
    
    /**
     * 源记录ID
     */
    String sourceRecordId;
    
    /**
     * 源记录版本（可选）
     */
    String sourceVersion;
    
    public SourceIdentifier(String sourceSystem, String sourceRecordId, String sourceVersion) {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("源系统标识不能为空");
        }
        if (sourceRecordId == null || sourceRecordId.trim().isEmpty()) {
            throw new IllegalArgumentException("源记录ID不能为空");
        }
        
        this.sourceSystem = sourceSystem.trim();
        this.sourceRecordId = sourceRecordId.trim();
        this.sourceVersion = sourceVersion != null ? sourceVersion.trim() : null;
    }
    
    /**
     * 创建不带版本的源标识
     */
    public static SourceIdentifier of(String sourceSystem, String sourceRecordId) {
        return new SourceIdentifier(sourceSystem, sourceRecordId, null);
    }
    
    /**
     * 创建带版本的源标识
     */
    public static SourceIdentifier of(String sourceSystem, String sourceRecordId, String sourceVersion) {
        return new SourceIdentifier(sourceSystem, sourceRecordId, sourceVersion);
    }
    
    /**
     * 获取唯一标识字符串
     */
    public String getUniqueKey() {
        if (sourceVersion != null) {
            return sourceSystem + ":" + sourceRecordId + ":" + sourceVersion;
        }
        return sourceSystem + ":" + sourceRecordId;
    }
    
    /**
     * 判断是否有版本信息
     */
    public boolean hasVersion() {
        return sourceVersion != null && !sourceVersion.isEmpty();
    }
    
    @Override
    public String toString() {
        return getUniqueKey();
    }
}
