package com.patra.ingest.domain.model.vo;

import lombok.Value;
import cn.hutool.core.util.StrUtil;

/**
 * 表达式哈希值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class ExprHash {
    
    String value;
    
    public ExprHash(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("表达式哈希不能为空");
        }
        if (value.length() != 64) {
            throw new IllegalArgumentException("表达式哈希必须是64位字符串");
        }
        // 验证是否为合法的十六进制字符串
        if (!value.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException("表达式哈希必须是有效的64位十六进制字符串");
        }
        this.value = value.toLowerCase();
    }
    
    @Override
    public String toString() {
        return value;
    }
}
