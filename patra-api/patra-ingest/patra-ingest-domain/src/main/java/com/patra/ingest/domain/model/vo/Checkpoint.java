package com.patra.ingest.domain.model.vo;

import lombok.Value;
import cn.hutool.core.util.StrUtil;

/**
 * 检查点值对象
 * 用于记录分页或令牌的检查点信息
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class Checkpoint {
    
    /**
     * 检查点类型（page/token/cursor等）
     */
    String type;
    
    /**
     * 检查点值
     */
    String value;
    
    /**
     * 附加元数据
     */
    String metadata;
    
    public Checkpoint(String type, String value, String metadata) {
        if (StrUtil.isBlank(type)) {
            throw new IllegalArgumentException("检查点类型不能为空");
        }
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("检查点值不能为空");
        }
        
        this.type = type.trim();
        this.value = value.trim();
        this.metadata = metadata != null ? metadata.trim() : null;
    }
    
    /**
     * 创建页码检查点
     */
    public static Checkpoint page(int pageNo, int pageSize) {
        return new Checkpoint("page", String.valueOf(pageNo), "size=" + pageSize);
    }
    
    /**
     * 创建令牌检查点
     */
    public static Checkpoint token(String tokenValue) {
        return new Checkpoint("token", tokenValue, null);
    }
    
    /**
     * 创建游标检查点
     */
    public static Checkpoint cursor(String cursorValue) {
        return new Checkpoint("cursor", cursorValue, null);
    }
    
    /**
     * 判断是否为页码类型
     */
    public boolean isPageType() {
        return "page".equals(type);
    }
    
    /**
     * 判断是否为令牌类型
     */
    public boolean isTokenType() {
        return "token".equals(type);
    }
    
    /**
     * 判断是否为游标类型
     */
    public boolean isCursorType() {
        return "cursor".equals(type);
    }
    
    /**
     * 获取页码（仅对page类型有效）
     */
    public Integer getPageNo() {
        if (!isPageType()) {
            throw new IllegalStateException("非页码类型的检查点");
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("无效的页码值: " + value);
        }
    }
}
