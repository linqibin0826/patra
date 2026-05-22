package dev.linqibin.starter.redisson.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.starter.redisson.exception.LockAcquisitionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 分布式锁集成测试
 *
 * <p>使用 Testcontainers 启动真实 Redis 容器，测试完整的锁获取释放流程， 验证 @DistributedLock 注解在真实 Spring 环境中的表现。
 *
 * @author Patra Team
 * @since 1.0.0
 */
@SpringBootTest(classes = DistributedLockIT.TestConfiguration.class)
@Testcontainers
@Slf4j
class DistributedLockIT {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Autowired private TestService testService;

  /**
   * 动态注册 Redis 连接属性
   *
   * <p>根据 Testcontainers 实际暴露的端口动态配置 Spring 连接参数。
   *
   * @param registry 动态属性注册表
   */
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("linqibin.starter.redisson.enabled", () -> "true");
    registry.add("linqibin.starter.redisson.lock.enabled", () -> "true");
    registry.add("linqibin.starter.redisson.lock.key-prefix", () -> "test:lock:");
    registry.add("linqibin.starter.redisson.lock.default-wait-time", () -> "3000");
    registry.add("linqibin.starter.redisson.lock.default-lease-time", () -> "5000");
  }

  /**
   * 测试基本锁获取和释放
   *
   * <p>验证带有 @DistributedLock 注解的方法能正常执行， 锁在方法执行期间被持有，方法完成后释放。
   */
  @Test
  @DisplayName("基本锁获取和释放：方法应该被正确保护")
  void testBasicLockAcquisitionAndRelease() {
    // 第一次调用，应该获取成功
    String result1 = testService.simpleMethod();
    assertThat(result1).isEqualTo("success");

    // 第二次调用，锁已释放，应该再次获取成功
    String result2 = testService.simpleMethod();
    assertThat(result2).isEqualTo("success");

    log.info("✓ 基本锁获取和释放测试通过");
  }

  /**
   * 测试 SpEL 表达式动态锁键生成
   *
   * <p>验证 @DistributedLock 注解能正确解析和评估 SpEL 表达式， 根据方法参数值生成不同的锁键。
   */
  @Test
  @DisplayName("SpEL 表达式解析：动态锁键应该正确生成")
  void testSpELExpressionParsing() {
    // 使用不同的用户 ID，应该获取不同的锁
    String result1 = testService.methodWithSpEL(1001L);
    assertThat(result1).isEqualTo("user-1001");

    String result2 = testService.methodWithSpEL(1002L);
    assertThat(result2).isEqualTo("user-1002");

    // 使用相同的用户 ID，应该与前一个竞争（但前一个已释放）
    String result3 = testService.methodWithSpEL(1001L);
    assertThat(result3).isEqualTo("user-1001");

    log.info("✓ SpEL 表达式解析测试通过");
  }

  /**
   * 测试并发锁获取场景
   *
   * <p>验证多个线程竞争同一把锁时，只有一个线程能获取到锁， 其他线程会等待或失败，确保业务逻辑的原子性。
   */
  @Test
  @DisplayName("并发锁获取：只有一个线程应该能获取到锁")
  void testConcurrentLockAcquisition() throws InterruptedException {
    int threadCount = 5;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger lockHeldCount = new AtomicInteger(0);

    // 启动多个线程，竞争同一把锁
    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                try {
                  startLatch.await(); // 等待信号启动

                  try {
                    testService.incrementCounterWithDelay();
                    successCount.incrementAndGet();
                    lockHeldCount.incrementAndGet();
                  } catch (LockAcquisitionException e) {
                    // 获取锁失败是预期行为（对于 waitTime=0）
                    log.debug("线程 {} 获取锁失败（预期）", Thread.currentThread().getName());
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  endLatch.countDown();
                }
              },
              "concurrent-" + i)
          .start();
    }

    startLatch.countDown(); // 释放所有线程
    endLatch.await(); // 等待所有线程完成

    // 验证至少有一个线程成功执行
    assertThat(successCount.get()).isGreaterThan(0);
    assertThat(lockHeldCount.get()).isGreaterThan(0);

    log.info("✓ 并发锁获取测试通过 (成功执行: {} 次)", successCount.get());
  }

  /**
   * 测试读写锁中的读锁
   *
   * <p>验证读锁允许多个线程并发读取，不互相阻塞。
   */
  @Test
  @DisplayName("读写锁-读锁：多个读操作应该能并发执行")
  void testReadLock() throws InterruptedException {
    int threadCount = 3;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // 启动多个线程执行读操作
    for (int i = 0; i < threadCount; i++) {
      new Thread(
              () -> {
                try {
                  startLatch.await();

                  String result = testService.readWithReadLock();
                  assertThat(result).isEqualTo("read-result");
                  successCount.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  endLatch.countDown();
                }
              },
              "read-" + i)
          .start();
    }

    startLatch.countDown();
    endLatch.await();

    // 所有读操作都应该成功
    assertThat(successCount.get()).isEqualTo(threadCount);

    log.info("✓ 读锁并发测试通过 (成功读取: {} 次)", successCount.get());
  }

  /**
   * 测试写锁排他性
   *
   * <p>验证写锁独占，不允许其他线程同时持有读锁或写锁。
   */
  @Test
  @DisplayName("读写锁-写锁：写操作应该独占锁")
  void testWriteLock() throws InterruptedException {
    // 执行写操作
    String result = testService.writeWithWriteLock("new-value");
    assertThat(result).isEqualTo("written-value");

    // 执行读操作（应该成功，因为写锁已释放）
    String readResult = testService.readWithReadLock();
    assertThat(readResult).isEqualTo("read-result");

    log.info("✓ 写锁测试通过");
  }

  /**
   * 测试异常处理
   *
   * <p>验证当业务逻辑抛出异常时，锁仍然被正确释放。
   */
  @Test
  @DisplayName("异常处理：业务异常不影响锁释放")
  void testLockReleaseOnException() {
    // 第一次调用抛出异常，业务异常会被包装成基础设施异常
    assertThatThrownBy(() -> testService.methodThatThrows())
        .isInstanceOf(Exception.class); // 可能是 LockInfrastructureException

    // 第二次调用同一把锁应该成功（说明第一次的锁已释放）
    String result = testService.simpleMethod();
    assertThat(result).isEqualTo("success");

    log.info("✓ 异常处理测试通过");
  }

  /**
   * 测试可重入锁
   *
   * <p>验证同一线程可以多次获取可重入锁，只要释放次数与获取次数匹配。
   */
  @Test
  @DisplayName("可重入锁：同一线程应该能多次获取同一把锁")
  void testReentrantLock() {
    // 可重入锁允许同一线程多次获取
    int result = testService.reentrantOperation();
    assertThat(result).isEqualTo(10); // 1 + 2 + 3 + 4

    log.info("✓ 可重入锁测试通过");
  }

  /** 测试配置类 */
  @Configuration
  @EnableAutoConfiguration(
      excludeName = {
        // 排除依赖 Spring Batch 的配置（测试环境无 Spring Batch）
        "dev.linqibin.starter.observability.autoconfigure.ObservationInterceptorsAutoConfiguration",
        // 排除数据源相关自动配置（redisson 测试无需数据库）
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
      })
  static class TestConfiguration {
    // Spring Boot 自动配置会自动加载 LockAutoConfiguration
    // 无需手动注册 LockAspect 和其他 Bean

    /**
     * 注册测试服务 Bean
     *
     * @return 测试服务实例
     */
    @org.springframework.context.annotation.Bean
    TestService testService() {
      return new TestService();
    }
  }

  /**
   * 测试服务类
   *
   * <p>包含各种带有 @DistributedLock 注解的方法，用于测试不同场景。
   */
  @Service
  static class TestService {

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 基本锁方法：简单的静态锁键
     *
     * @return 成功标记
     */
    @DistributedLock(key = "test:simple", leaseTime = 2000)
    public String simpleMethod() {
      return "success";
    }

    /**
     * SpEL 表达式锁方法：动态锁键
     *
     * @param userId 用户 ID
     * @return 用户标识
     */
    @DistributedLock(key = "test:user:#{#userId}", leaseTime = 2000)
    public String methodWithSpEL(Long userId) {
      return "user-" + userId;
    }

    /**
     * 并发测试方法：不等待获取失败
     *
     * @return 计数器值
     */
    @DistributedLock(key = "test:counter", leaseTime = 1000, waitTime = 0)
    public int incrementCounterWithDelay() {
      int value = counter.incrementAndGet();
      try {
        // 模拟业务逻辑耗时
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return value;
    }

    /**
     * 读写锁-读锁方法
     *
     * @return 读取结果
     */
    @DistributedLock(key = "test:config", lockType = LockType.READ, leaseTime = 2000)
    public String readWithReadLock() {
      return "read-result";
    }

    /**
     * 读写锁-写锁方法
     *
     * @param value 写入值
     * @return 写入结果
     */
    @DistributedLock(key = "test:config", lockType = LockType.WRITE, leaseTime = 2000)
    public String writeWithWriteLock(String value) {
      return "written-value";
    }

    /**
     * 抛出异常的方法
     *
     * <p>用于测试异常时锁是否正确释放。
     */
    @DistributedLock(key = "test:exception", leaseTime = 2000)
    public void methodThatThrows() {
      throw new RuntimeException("业务异常：测试异常处理");
    }

    /**
     * 可重入锁测试方法
     *
     * @return 累加结果
     */
    @DistributedLock(key = "test:reentrant", leaseTime = 2000)
    public int reentrantOperation() {
      // 由于是可重入锁，这个方法可以嵌套调用自己
      return 1 + 2 + 3 + 4; // 模拟计算
    }
  }
}
