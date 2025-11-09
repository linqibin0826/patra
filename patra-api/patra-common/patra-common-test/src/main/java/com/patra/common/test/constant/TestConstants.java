package com.patra.common.test.constant;

/**
 * 测试常量定义
 *
 * <p>定义测试中常用的常量,包括测试用户、组织、超时时间等。
 * 使用统一的常量可以提高测试代码的一致性和可维护性。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Test
 * void testUserCreation() {
 *     User user = new User(TestConstants.TEST_USER_ID, "Alice");
 *     assertThat(user.getId()).isEqualTo(TestConstants.TEST_USER_ID);
 * }
 *
 * @Test
 * @Timeout(value = TestConstants.TEST_TIMEOUT_MS, unit = TimeUnit.MILLISECONDS)
 * void testAsyncOperation() {
 *     // 异步操作测试
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class TestConstants {

    private TestConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 测试用户 ID
     */
    public static final String TEST_USER_ID = "test-user-001";

    /**
     * 测试组织 ID
     */
    public static final String TEST_ORG_ID = "test-org-001";

    /**
     * 测试超时时间(毫秒)
     */
    public static final int TEST_TIMEOUT_MS = 5000;

    /**
     * 测试重试次数
     */
    public static final int TEST_RETRY_COUNT = 3;

    /**
     * 测试数据库名称
     */
    public static final String TEST_DB_NAME = "test_db";

    /**
     * 测试 Redis Key 前缀
     */
    public static final String TEST_REDIS_KEY_PREFIX = "test:";

    /**
     * 测试邮箱域名
     */
    public static final String TEST_EMAIL_DOMAIN = "@test.patra.com";

    /**
     * 测试 URL 前缀
     */
    public static final String TEST_URL_PREFIX = "https://test.patra.com";
}
