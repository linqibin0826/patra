package com.patra.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HashUtils 的基础单元测试。
 */
class HashUtilsTest {

    @Test
    void sha256_and_hex_should_work_with_null_and_empty() {
        // null → 以空字节处理
        assertThat(HashUtils.sha256( (String) null )).hasSize(32);
        assertThat(HashUtils.sha256Hex( (String) null )).hasSize(64);

        // 空串
        assertThat(HashUtils.sha256("")).hasSize(32);
        assertThat(HashUtils.sha256Hex("")).hasSize(64);

        // 十六进制转换
        assertThat(HashUtils.toHex(new byte[0])).isEmpty();
        assertThat(HashUtils.toHex(new byte[]{ (byte)0xAB, (byte)0xCD })).isEqualTo("abcd");
    }

    @Test
    void sha256_should_match_known_value() {
        // 来自 openssl/在线工具校验值
        String hex = HashUtils.sha256Hex("papertrace");
        assertThat(hex).isEqualTo("2f0afd03c59de9bf6172c159499cc04de06f26c4318f0a6d3865f3d635de23eb");
    }
}
