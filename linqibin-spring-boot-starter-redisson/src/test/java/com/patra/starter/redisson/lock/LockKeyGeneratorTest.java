package com.patra.starter.redisson.lock;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.redisson.config.RedissonProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LockKeyGenerator} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockKeyGenerator 锁键生成器测试")
class LockKeyGeneratorTest {

  private LockKeyGenerator generator;
  private RedissonProperties properties;

  @BeforeEach
  void setUp() {
    properties = new RedissonProperties();
    properties.getLock().setKeyPrefix("test:lock:");
    generator = new LockKeyGenerator(properties);
  }

  @Test
  @DisplayName("应该正确生成静态字符串锁键（无 SpEL 表达式）")
  void shouldGenerateStaticKey() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    String key = generator.generateKey("user:123", method, new Object[] {"param"});

    assertThat(key).isEqualTo("test:lock:user:123");
  }

  @Test
  @DisplayName("应该正确解析 SpEL 表达式（单个参数）")
  void shouldParseSpELWithSingleParameter() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    String key = generator.generateKey("user:#{#userId}", method, new Object[] {"123"});

    assertThat(key).isEqualTo("test:lock:user:123");
  }

  @Test
  @DisplayName("应该正确解析 SpEL 表达式（多个参数）")
  void shouldParseSpELWithMultipleParameters() throws Exception {
    Method method =
        getClass().getDeclaredMethod("dummyMethodMultiParams", String.class, String.class);
    String key =
        generator.generateKey(
            "user:#{#userId}:action:#{#action}", method, new Object[] {"123", "update"});

    assertThat(key).isEqualTo("test:lock:user:123:action:update");
  }

  @Test
  @DisplayName("应该正确解析 SpEL 表达式（对象属性）")
  void shouldParseSpELWithObjectProperty() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethodWithObject", UserRequest.class);
    UserRequest request = new UserRequest("user-123", "update");
    String key = generator.generateKey("user:#{#request.userId}", method, new Object[] {request});

    assertThat(key).isEqualTo("test:lock:user:user-123");
  }

  @Test
  @DisplayName("SpEL 表达式应该被缓存")
  void shouldCacheSpELExpression() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);

    // 第一次解析
    generator.generateKey("user:#{#userId}", method, new Object[] {"123"});
    int cacheSize1 = generator.getCacheSize();

    // 第二次使用相同表达式
    generator.generateKey("user:#{#userId}", method, new Object[] {"456"});
    int cacheSize2 = generator.getCacheSize();

    // 缓存大小不应该增加
    assertThat(cacheSize1).isEqualTo(1);
    assertThat(cacheSize2).isEqualTo(1);
  }

  @Test
  @DisplayName("静态字符串不应该被缓存到 SpEL 缓存")
  void staticString_ShouldNotBeCached() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);

    generator.generateKey("user:123", method, new Object[] {"param"});

    assertThat(generator.getCacheSize()).isEqualTo(0);
  }

  @Test
  @DisplayName("应该正确清除缓存")
  void shouldClearCache() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);

    generator.generateKey("user:#{#userId}", method, new Object[] {"123"});
    assertThat(generator.getCacheSize()).isEqualTo(1);

    generator.clearCache();
    assertThat(generator.getCacheSize()).isEqualTo(0);
  }

  @Test
  @DisplayName("SpEL 表达式引用不存在的变量应该返回 null 占位符")
  void invalidSpEL_ShouldReturnNullPlaceholder() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);

    // SpEL 模板解析器对不存在的变量会返回 null，然后拼接为 "user:null"
    String key = generator.generateKey("user:#{#invalidParam}", method, new Object[] {"123"});

    // 注意：SpEL 模板解析器会将 null 转换为字符串 "null"
    assertThat(key).isEqualTo("test:lock:user:");
  }

  @Test
  @DisplayName("SpEL 表达式参数值为 null 应该返回空字符串")
  void spELResultNull_ShouldReturnEmptyString() throws Exception {
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);

    // SpEL 模板解析器对 null 值会返回空字符串
    String key = generator.generateKey("user:#{#userId}", method, new Object[] {null});

    assertThat(key).isEqualTo("test:lock:user:");
  }

  @Test
  @DisplayName("应该使用配置的前缀")
  void shouldUseConfiguredPrefix() throws Exception {
    properties.getLock().setKeyPrefix("custom:prefix:");
    LockKeyGenerator customGenerator = new LockKeyGenerator(properties);

    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    String key = customGenerator.generateKey("user:123", method, new Object[] {"param"});

    assertThat(key).isEqualTo("custom:prefix:user:123");
  }

  // ===== 测试辅助方法 =====

  @SuppressWarnings("unused")
  private void dummyMethod(String userId) {}

  @SuppressWarnings("unused")
  private void dummyMethodMultiParams(String userId, String action) {}

  @SuppressWarnings("unused")
  private void dummyMethodWithObject(UserRequest request) {}

  // ===== 测试用 DTO =====

  private static class UserRequest {
    private final String userId;
    private final String action;

    public UserRequest(String userId, String action) {
      this.userId = userId;
      this.action = action;
    }

    public String getUserId() {
      return userId;
    }

    public String getAction() {
      return action;
    }
  }
}
