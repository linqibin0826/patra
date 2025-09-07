package com.patra.ingest.domain.model.vo;

import lombok.Value;

/**
 * 游标键值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class CursorKey {
    
    String value;
    
    public CursorKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("游标键不能为空");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("游标键长度不能超过64个字符");
        }
        this.value = value.trim();
    }
    
    @Override
    public String toString() {
        return value;
    }
}
