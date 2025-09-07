package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorKey;
import com.patra.ingest.domain.model.vo.CursorState;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * 游标聚合根
 * 管理数据源的游标状态和位置信息
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
@With
public class Cursor {
    
    /**
     * 聚合根ID
     */
    Long id;
    
    /**
     * 关联任务ID
     */
    Long jobId;
    
    /**
     * 游标键
     */
    CursorKey cursorKey;
    
    /**
     * 游标类型
     */
    CursorType type;
    
    /**
     * 游标状态
     */
    CursorState state;
    
    /**
     * 扩展属性（存储额外的游标相关信息）
     */
    @Builder.Default
    Map<String, String> properties = new HashMap<>();
    
    /**
     * 备注信息
     */
    String remarks;
    
    /**
     * 乐观锁版本号
     */
    Long version;
    
    /**
     * 创建时间
     */
    LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    LocalDateTime updatedAt;
    
    /**
     * 验证游标的业务规则
     */
    public void validate() {
        if (jobId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        if (cursorKey == null) {
            throw new IllegalArgumentException("游标键不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("游标类型不能为空");
        }
        if (state == null) {
            throw new IllegalArgumentException("游标状态不能为空");
        }
        
        // 根据类型验证游标值格式
        validateCursorValueByType();
    }
    
    /**
     * 根据游标类型验证游标值格式
     */
    private void validateCursorValueByType() {
        String value = state.getValue();
        
        switch (type) {
            case PAGE:
                try {
                    int page = Integer.parseInt(value);
                    if (page < 0) {
                        throw new IllegalArgumentException("分页游标值必须为非负整数");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("分页游标值必须为有效整数");
                }
                break;
                
            case TIMESTAMP:
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("时间戳游标值必须为有效长整型");
                }
                break;
                
            case TOKEN:
            case CUSTOM:
                // TOKEN和CUSTOM类型的游标值格式由具体业务决定，这里不做严格验证
                break;
                
            default:
                throw new IllegalArgumentException("不支持的游标类型: " + type);
        }
    }
    
    /**
     * 更新游标值
     */
    public Cursor updateValue(String newValue) {
        if (state.getFinished()) {
            throw new IllegalStateException("已完成的游标不能更新值");
        }
        
        CursorState newState = state.updateValue(newValue);
        return this.withState(newState).withUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 标记游标为已完成
     */
    public Cursor markFinished() {
        CursorState newState = state.markFinished();
        return this.withState(newState).withUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 设置下次检查时间
     */
    public Cursor scheduleNextCheck(LocalDateTime nextCheckAt) {
        if (state.getFinished()) {
            throw new IllegalStateException("已完成的游标不需要设置检查时间");
        }
        
        CursorState newState = state.withNextCheck(nextCheckAt);
        return this.withState(newState).withUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 判断游标是否已完成
     */
    public boolean isFinished() {
        return state.getFinished();
    }
    
    /**
     * 判断是否需要检查
     */
    public boolean needsCheck() {
        return state.needsCheck();
    }
    
    /**
     * 获取游标当前值
     */
    public String getCurrentValue() {
        return state.getValue();
    }
    
    /**
     * 获取下一个页码（仅适用于分页类型）
     */
    public Integer getNextPage() {
        if (type != CursorType.PAGE) {
            throw new IllegalStateException("只有分页类型的游标才能获取下一页码");
        }
        
        try {
            int currentPage = Integer.parseInt(state.getValue());
            return currentPage + 1;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("分页游标值格式错误: " + state.getValue());
        }
    }
    
    /**
     * 设置扩展属性
     */
    public Cursor setProperty(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("属性键不能为空");
        }
        
        Map<String, String> newProperties = new HashMap<>(this.properties);
        if (value == null) {
            newProperties.remove(key);
        } else {
            newProperties.put(key, value);
        }
        
        return this.withProperties(newProperties).withUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 获取扩展属性
     */
    public String getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * 获取扩展属性，带默认值
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
    
    /**
     * 判断是否包含指定属性
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * 重置游标（将值重置为初始状态）
     */
    public Cursor reset(String initialValue) {
        CursorState newState = CursorState.create(initialValue);
        return this.withState(newState).withUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 创建游标的副本（用于备份或测试）
     */
    public Cursor copy() {
        return this.withId(null)
                   .withVersion(null)
                   .withCreatedAt(null)
                   .withUpdatedAt(null);
    }
}
