package com.patra.ingest.domain.model.vo;

import lombok.Value;
import java.time.LocalDateTime;

/**
 * 游标状态值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class CursorState {
    
    /**
     * 游标值
     */
    String value;
    
    /**
     * 是否已完成
     */
    Boolean finished;
    
    /**
     * 下次检查时间
     */
    LocalDateTime nextCheckAt;
    
    /**
     * 最后更新时间
     */
    LocalDateTime lastUpdatedAt;
    
    public CursorState(String value, Boolean finished, LocalDateTime nextCheckAt, LocalDateTime lastUpdatedAt) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("游标值不能为空");
        }
        if (finished == null) {
            throw new IllegalArgumentException("完成状态不能为空");
        }
        if (lastUpdatedAt == null) {
            throw new IllegalArgumentException("最后更新时间不能为空");
        }
        
        this.value = value.trim();
        this.finished = finished;
        this.nextCheckAt = nextCheckAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }
    
    /**
     * 创建新的游标状态
     */
    public static CursorState create(String value) {
        return new CursorState(value, false, null, LocalDateTime.now());
    }
    
    /**
     * 标记为已完成
     */
    public CursorState markFinished() {
        return new CursorState(this.value, true, null, LocalDateTime.now());
    }
    
    /**
     * 更新游标值
     */
    public CursorState updateValue(String newValue) {
        return new CursorState(newValue, this.finished, this.nextCheckAt, LocalDateTime.now());
    }
    
    /**
     * 设置下次检查时间
     */
    public CursorState withNextCheck(LocalDateTime nextCheckAt) {
        return new CursorState(this.value, this.finished, nextCheckAt, LocalDateTime.now());
    }
    
    /**
     * 判断是否需要检查
     */
    public boolean needsCheck() {
        if (finished) {
            return false;
        }
        if (nextCheckAt == null) {
            return true;
        }
        return !LocalDateTime.now().isBefore(nextCheckAt);
    }
}
