package com.patra.ingest.domain.model.vo;

import lombok.Value;
import cn.hutool.core.util.StrUtil;

/**
 * 游标键值对值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class CursorPair {
    
    String key;
    String value;
    
    public CursorPair(String key, String value) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("游标键不能为空");
        }
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("游标值不能为空");
        }
        if (key.length() > 64) {
            throw new IllegalArgumentException("游标键长度不能超过64个字符");
        }
        if (value.length() > 1024) {
            throw new IllegalArgumentException("游标值长度不能超过1024个字符");
        }
        this.key = key.trim();
        this.value = value.trim();
    }
}
