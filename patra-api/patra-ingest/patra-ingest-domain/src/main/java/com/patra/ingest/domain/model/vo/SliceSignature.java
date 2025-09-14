package com.patra.ingest.domain.model.vo;

import lombok.Value;

/**
 * 切片签名值对象。
 * <p>
 * 表示切片的唯一标识和边界信息，用于幂等性保证。
 * 包含切片的哈希签名和规范化的边界描述。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class SliceSignature {

    /** 切片签名哈希（SHA-256） */
    String hash;

    /** 切片边界描述（JSON格式） */
    String spec;

    /**
     * 创建切片签名。
     *
     * @param hash 签名哈希
     * @param spec 边界描述
     * @return 切片签名
     */
    public static SliceSignature of(String hash, String spec) {
        if (hash == null || hash.trim().isEmpty()) {
            throw new IllegalArgumentException("切片签名哈希不能为空");
        }
        return new SliceSignature(hash.trim(), spec);
    }

    /**
     * 检查签名是否有效。
     *
     * @return 如果哈希不为空则返回 true
     */
    public boolean isValid() {
        return hash != null && !hash.trim().isEmpty();
    }
}
