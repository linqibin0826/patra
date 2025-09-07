package com.patra.ingest.domain.model.vo;

import lombok.Value;
import java.time.LocalDateTime;

/**
 * 命中元数据值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class HitMetadata {
    
    /**
     * 数据哈希值（用于去重和变更检测）
     */
    String dataHash;
    
    /**
     * 数据大小（字节）
     */
    Long dataSize;
    
    /**
     * 源系统时间戳
     */
    LocalDateTime sourceTimestamp;
    
    /**
     * 处理时间戳
     */
    LocalDateTime processedAt;
    
    /**
     * 扩展信息（JSON格式）
     */
    String extensions;
    
    public HitMetadata(String dataHash, Long dataSize, LocalDateTime sourceTimestamp, 
                      LocalDateTime processedAt, String extensions) {
        if (dataHash == null || dataHash.trim().isEmpty()) {
            throw new IllegalArgumentException("数据哈希值不能为空");
        }
        if (processedAt == null) {
            throw new IllegalArgumentException("处理时间戳不能为空");
        }
        
        this.dataHash = dataHash.trim();
        this.dataSize = dataSize;
        this.sourceTimestamp = sourceTimestamp;
        this.processedAt = processedAt;
        this.extensions = extensions;
    }
    
    /**
     * 创建基础元数据
     */
    public static HitMetadata create(String dataHash) {
        return new HitMetadata(dataHash, null, null, LocalDateTime.now(), null);
    }
    
    /**
     * 创建完整元数据
     */
    public static HitMetadata create(String dataHash, Long dataSize, LocalDateTime sourceTimestamp) {
        return new HitMetadata(dataHash, dataSize, sourceTimestamp, LocalDateTime.now(), null);
    }
    
    /**
     * 带扩展信息创建
     */
    public static HitMetadata create(String dataHash, Long dataSize, LocalDateTime sourceTimestamp, String extensions) {
        return new HitMetadata(dataHash, dataSize, sourceTimestamp, LocalDateTime.now(), extensions);
    }
    
    /**
     * 判断是否有扩展信息
     */
    public boolean hasExtensions() {
        return extensions != null && !extensions.trim().isEmpty();
    }
    
    /**
     * 判断两个元数据的数据是否相同（基于哈希值）
     */
    public boolean sameData(HitMetadata other) {
        return this.dataHash.equals(other.dataHash);
    }
}
