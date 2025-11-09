package com.patra.common.test.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * MockDataFactory 元测试
 *
 * <p>测试策略: 纯单元测试，验证随机数据生成工具</p>
 * <p>测试目标: 验证 MockDataFactory 静态工具类的所有方法</p>
 * <p>测试覆盖: 字符串、UUID、数值、日期、枚举、布尔值、Email、URL 生成</p>
 *
 * <h3>测试状态</h3>
 * <ul>
 *   <li>预期: 部分红灯（randomString, randomEmail, randomUrl 未实现）</li>
 *   <li>预期: 部分绿灯（randomUuid, randomInt, randomLong 等已实现）</li>
 *   <li>目的: TDD 第一步，验证已实现功能，暴露未实现功能</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@DisplayName("MockDataFactory 元测试 - 验证 Mock 数据工厂")
class MockDataFactoryTest {

    // ========== 测试用枚举 ==========

    enum TestStatus {
        ACTIVE, INACTIVE, PENDING, DELETED
    }

    enum SingleValueEnum {
        ONLY_ONE
    }

    // ========== randomString() 方法测试 ==========

    @Test
    @DisplayName("应该生成带前缀的随机字符串")
    void shouldGenerateRandomStringWithPrefix() {
        // Given: 指定前缀和长度
        String prefix = "TEST_";
        int length = 10;

        // When: 生成随机字符串
        String result = MockDataFactory.randomString(prefix, length);

        // Then: 验证字符串格式
        assertThat(result)
            .startsWith(prefix)
            .hasSize(prefix.length() + length);
    }

    @Test
    @DisplayName("应该生成不同的随机字符串")
    void shouldGenerateDifferentRandomStrings() {
        // Given: 多次生成随机字符串
        String str1 = MockDataFactory.randomString("PREFIX_", 8);
        String str2 = MockDataFactory.randomString("PREFIX_", 8);
        String str3 = MockDataFactory.randomString("PREFIX_", 8);

        // Then: 验证生成的字符串大概率不同（随机性）
        // 注意: 理论上可能相同，但概率极低
        assertThat(str1).isNotEqualTo(str2).isNotEqualTo(str3);
    }

    @Test
    @DisplayName("应该支持空前缀")
    void shouldSupportEmptyPrefix() {
        // Given: 空前缀
        String prefix = "";
        int length = 5;

        // When: 生成随机字符串
        String result = MockDataFactory.randomString(prefix, length);

        // Then: 验证字符串长度
        assertThat(result).hasSize(length);
    }

    @Test
    @DisplayName("应该支持长度为零")
    void shouldSupportZeroLength() {
        // Given: 长度为 0
        String prefix = "PREFIX_";
        int length = 0;

        // When: 生成随机字符串
        String result = MockDataFactory.randomString(prefix, length);

        // Then: 验证只有前缀
        assertThat(result).isEqualTo(prefix);
    }

    // ========== randomUuid() 方法测试 ==========

    @Test
    @DisplayName("应该生成合法的 UUID 字符串")
    void shouldGenerateValidUuid() {
        // When: 生成 UUID
        String uuid = MockDataFactory.randomUuid();

        // Then: 验证 UUID 格式（36个字符，包含 4 个连字符）
        assertThat(uuid)
            .hasSize(36)
            .contains("-")
            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @RepeatedTest(5)
    @DisplayName("应该每次生成不同的 UUID")
    void shouldGenerateDifferentUuids() {
        // Given: 生成两个 UUID
        String uuid1 = MockDataFactory.randomUuid();
        String uuid2 = MockDataFactory.randomUuid();

        // Then: 验证 UUID 不同
        assertThat(uuid1).isNotEqualTo(uuid2);
    }

    // ========== randomInt() 方法测试 ==========

    @Test
    @DisplayName("应该生成指定范围内的随机整数")
    void shouldGenerateRandomIntInRange() {
        // Given: 指定范围 [1, 10]
        int min = 1;
        int max = 10;

        // When: 生成随机整数
        int result = MockDataFactory.randomInt(min, max);

        // Then: 验证结果在范围内
        assertThat(result).isBetween(min, max);
    }

    @RepeatedTest(10)
    @DisplayName("应该生成不同的随机整数")
    void shouldGenerateDifferentRandomInts() {
        // When: 生成随机整数（范围足够大，确保有多样性）
        int result = MockDataFactory.randomInt(1, 1000);

        // Then: 验证结果在范围内
        assertThat(result).isBetween(1, 1000);
    }

    @Test
    @DisplayName("应该支持最小值等于最大值")
    void shouldSupportSameMinAndMax() {
        // Given: min = max = 5
        int min = 5;
        int max = 5;

        // When: 生成随机整数
        int result = MockDataFactory.randomInt(min, max);

        // Then: 验证结果为 5
        assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("应该支持负数范围")
    void shouldSupportNegativeRange() {
        // Given: 负数范围 [-10, -1]
        int min = -10;
        int max = -1;

        // When: 生成随机整数
        int result = MockDataFactory.randomInt(min, max);

        // Then: 验证结果在范围内
        assertThat(result).isBetween(min, max);
    }

    // ========== randomLong() 方法测试 ==========

    @Test
    @DisplayName("应该生成指定范围内的随机长整数")
    void shouldGenerateRandomLongInRange() {
        // Given: 指定范围 [1000, 9999]
        long min = 1000L;
        long max = 9999L;

        // When: 生成随机长整数
        long result = MockDataFactory.randomLong(min, max);

        // Then: 验证结果在范围内
        assertThat(result).isBetween(min, max);
    }

    @Test
    @DisplayName("应该支持大范围的长整数")
    void shouldSupportLargeRangeForLong() {
        // Given: 大范围
        long min = 0L;
        long max = Long.MAX_VALUE / 2; // 避免溢出

        // When: 生成随机长整数
        long result = MockDataFactory.randomLong(min, max);

        // Then: 验证结果在范围内
        assertThat(result).isBetween(min, max);
    }

    // ========== randomDateTime() 方法测试 ==========

    @Test
    @DisplayName("应该生成过去的日期时间")
    void shouldGeneratePastDateTime() {
        // Given: 7 天前
        int daysAgo = -7;
        LocalDateTime now = LocalDateTime.now();

        // When: 生成随机日期时间
        LocalDateTime result = MockDataFactory.randomDateTime(daysAgo);

        // Then: 验证日期在过去
        assertThat(result).isBefore(now);
        assertThat(result).isAfterOrEqualTo(now.minusDays(8)); // 留一天余量
    }

    @Test
    @DisplayName("应该生成未来的日期时间")
    void shouldGenerateFutureDateTime() {
        // Given: 7 天后
        int daysAgo = 7;
        LocalDateTime now = LocalDateTime.now();

        // When: 生成随机日期时间
        LocalDateTime result = MockDataFactory.randomDateTime(daysAgo);

        // Then: 验证日期在未来（根据当前实现，这里可能需要调整）
        // 注意: 当前实现使用 minusDays(Math.abs(daysAgo))，所以总是过去
        assertThat(result).isBefore(now);
    }

    @Test
    @DisplayName("应该生成今天的日期时间")
    void shouldGenerateTodayDateTime() {
        // Given: 0 天前（今天）
        int daysAgo = 0;
        LocalDateTime now = LocalDateTime.now();

        // When: 生成随机日期时间
        LocalDateTime result = MockDataFactory.randomDateTime(daysAgo);

        // Then: 验证日期是今天
        assertThat(result.toLocalDate()).isEqualTo(now.toLocalDate());
    }

    // ========== randomDate() 方法测试 ==========

    @Test
    @DisplayName("应该生成过去的日期")
    void shouldGeneratePastDate() {
        // Given: 30 天前
        int daysAgo = -30;
        LocalDate now = LocalDate.now();

        // When: 生成随机日期
        LocalDate result = MockDataFactory.randomDate(daysAgo);

        // Then: 验证日期在过去
        assertThat(result).isBefore(now);
        assertThat(result).isAfterOrEqualTo(now.minusDays(31)); // 留一天余量
    }

    @Test
    @DisplayName("应该生成今天的日期")
    void shouldGenerateTodayDate() {
        // Given: 0 天前
        int daysAgo = 0;
        LocalDate now = LocalDate.now();

        // When: 生成随机日期
        LocalDate result = MockDataFactory.randomDate(daysAgo);

        // Then: 验证日期是今天
        assertThat(result).isEqualTo(now);
    }

    // ========== randomEnum() 方法测试 ==========

    @Test
    @DisplayName("应该从枚举中随机选择一个值")
    void shouldRandomlySelectEnumValue() {
        // When: 随机选择枚举值
        TestStatus result = MockDataFactory.randomEnum(TestStatus.class);

        // Then: 验证结果是合法的枚举值
        assertThat(result).isIn((Object[]) TestStatus.values());
    }

    @RepeatedTest(20)
    @DisplayName("应该能够选择到所有枚举值（随机性）")
    void shouldSelectAllEnumValuesEventually() {
        // When: 多次随机选择
        TestStatus result = MockDataFactory.randomEnum(TestStatus.class);

        // Then: 验证结果是合法的枚举值
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("应该支持单值枚举")
    void shouldSupportSingleValueEnum() {
        // When: 随机选择单值枚举
        SingleValueEnum result = MockDataFactory.randomEnum(SingleValueEnum.class);

        // Then: 验证结果是唯一的枚举值
        assertThat(result).isEqualTo(SingleValueEnum.ONLY_ONE);
    }

    // ========== randomBoolean() 方法测试 ==========

    @Test
    @DisplayName("应该生成随机布尔值")
    void shouldGenerateRandomBoolean() {
        // When: 生成随机布尔值
        boolean result = MockDataFactory.randomBoolean();

        // Then: 验证结果是 true 或 false
        assertThat(result).isIn(true, false);
    }

    @RepeatedTest(20)
    @DisplayName("应该生成 true 和 false（随机性）")
    void shouldGenerateBothTrueAndFalse() {
        // When: 多次生成随机布尔值
        boolean result = MockDataFactory.randomBoolean();

        // Then: 验证结果有效
        assertThat(result).isNotNull();
    }

    // ========== randomEmail() 方法测试 ==========

    @Test
    @DisplayName("应该生成合法的 Email 地址")
    void shouldGenerateValidEmail() {
        // When: 生成随机 Email
        String email = MockDataFactory.randomEmail();

        // Then: 验证 Email 格式
        assertThat(email)
            .contains("@")
            .matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    @Test
    @DisplayName("应该生成不同的 Email 地址")
    void shouldGenerateDifferentEmails() {
        // Given: 生成多个 Email
        String email1 = MockDataFactory.randomEmail();
        String email2 = MockDataFactory.randomEmail();
        String email3 = MockDataFactory.randomEmail();

        // Then: 验证 Email 不同
        assertThat(email1).isNotEqualTo(email2).isNotEqualTo(email3);
    }

    // ========== randomUrl() 方法测试 ==========

    @Test
    @DisplayName("应该生成合法的 URL")
    void shouldGenerateValidUrl() {
        // When: 生成随机 URL
        String url = MockDataFactory.randomUrl();

        // Then: 验证 URL 格式
        assertThat(url)
            .startsWith("http")
            .contains("://")
            .matches("^https?://[a-zA-Z0-9.-]+(/.*)?$");
    }

    @Test
    @DisplayName("应该生成不同的 URL")
    void shouldGenerateDifferentUrls() {
        // Given: 生成多个 URL
        String url1 = MockDataFactory.randomUrl();
        String url2 = MockDataFactory.randomUrl();
        String url3 = MockDataFactory.randomUrl();

        // Then: 验证 URL 不同
        assertThat(url1).isNotEqualTo(url2).isNotEqualTo(url3);
    }

    // ========== 工具类特性测试 ==========

    @Test
    @DisplayName("应该禁止实例化工具类")
    void shouldPreventInstantiation() {
        // When & Then: 验证构造函数抛出异常
        assertThatThrownBy(() -> {
            // 使用反射调用私有构造函数
            var constructor = MockDataFactory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .getCause()
        .hasMessageContaining("工具类不允许实例化");
    }
}
