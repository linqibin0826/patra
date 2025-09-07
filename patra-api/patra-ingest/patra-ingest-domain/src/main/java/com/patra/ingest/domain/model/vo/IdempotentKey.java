package com.patra.ingest.domain.model.vo;

import lombok.Value;
import cn.hutool.core.util.StrUtil;

/**
 * 幂等键值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class IdempotentKey {
    
    String value;
    
    public IdempotentKey(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("幂等键长度不能超过64个字符");
        }
        this.value = value.trim();
    }
    
    @Override
    public String toString() {
        return value;
    }
}
