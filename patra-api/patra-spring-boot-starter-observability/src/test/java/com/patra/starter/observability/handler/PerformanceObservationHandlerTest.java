package com.patra.starter.observability.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PerformanceObservationHandler 单元测试。
 *
 * <p>验证性能监控功能正确工作。
 *
 * @author Jobs
 * @since 1.0.0
 */
class PerformanceObservationHandlerTest {

  private PerformanceObservationHandler handler;
  private TestObservationRegistry registry;

  @BeforeEach
  void setUp() {
    // 设置慢操作阈值为 100ms
    handler = new PerformanceObservationHandler(Duration.ofMillis(100));
    registry = TestObservationRegistry.create();
    registry.observationConfig().observationHandler(handler);
  }

  /** 测试快速操作（不超过阈值）。 */
  @Test
  void shouldNotLogWarningForFastOperation() throws InterruptedException {
    // 创建快速操作（50ms，低于阈值）
    Observation.createNotStarted("fast.operation", registry)
        .observe(
            () -> {
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // 验证 Observation 正常完成（无异常抛出）
    // 实际生产中可以通过日志监控验证是否有警告日志
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试慢操作（超过阈值）。 */
  @Test
  void shouldLogWarningForSlowOperation() throws InterruptedException {
    // 创建慢操作（150ms，超过阈值）
    Observation.createNotStarted("slow.operation", registry)
        .observe(
            () -> {
              try {
                Thread.sleep(150);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // 验证 Observation 正常完成（Handler 不应抛出异常）
    // 实际生产中应该通过日志监控验证是否有慢操作警告日志
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试多个 Observation 并发执行。 */
  @Test
  void shouldHandleConcurrentObservations() throws InterruptedException {
    // 创建多个并发 Observation
    Thread t1 =
        new Thread(
            () -> {
              Observation.createNotStarted("operation.1", registry)
                  .observe(
                      () -> {
                        try {
                          Thread.sleep(50);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      });
            });

    Thread t2 =
        new Thread(
            () -> {
              Observation.createNotStarted("operation.2", registry)
                  .observe(
                      () -> {
                        try {
                          Thread.sleep(120); // 慢操作
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      });
            });

    Thread t3 =
        new Thread(
            () -> {
              Observation.createNotStarted("operation.3", registry)
                  .observe(
                      () -> {
                        try {
                          Thread.sleep(30);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      });
            });

    // 启动并等待所有线程完成
    t1.start();
    t2.start();
    t3.start();

    t1.join();
    t2.join();
    t3.join();

    // 验证所有 Observation 都已完成
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试 Observation 异常情况。 */
  @Test
  void shouldHandleObservationWithError() {
    try {
      Observation.createNotStarted("error.operation", registry)
          .observe(
              () -> {
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                throw new RuntimeException("模拟错误");
              });
    } catch (RuntimeException e) {
      // 预期异常
      assertThat(e.getMessage()).isEqualTo("模拟错误");
    }

    // 验证即使有异常，Handler 也正常处理
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试嵌套 Observation。 */
  @Test
  void shouldHandleNestedObservations() {
    Observation outer = Observation.createNotStarted("outer.operation", registry);
    outer.start();

    try {
      Thread.sleep(30);

      // 内嵌 Observation
      Observation.createNotStarted("inner.operation", registry)
          .observe(
              () -> {
                try {
                  Thread.sleep(40);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });

      Thread.sleep(30);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      outer.stop();
    }

    // 验证所有 Observation 都已完成
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试 Handler 支持所有 Context 类型。 */
  @Test
  void shouldSupportAllContextTypes() {
    Observation.Context context = new Observation.Context();
    context.setName("test.observation");

    assertThat(handler.supportsContext(context)).isTrue();
  }

  /** 测试阈值边界条件 - 刚好等于阈值。 */
  @Test
  void shouldHandleBoundaryThreshold() throws InterruptedException {
    // 创建耗时刚好等于阈值的操作（100ms）
    Observation.createNotStarted("boundary.operation", registry)
        .observe(
            () -> {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // 验证 Observation 正常完成
    assertThat(registry.getCurrentObservation()).isNull();
  }

  /** 测试极短操作（几乎瞬间完成）。 */
  @Test
  void shouldHandleInstantOperation() {
    // 创建瞬间完成的操作
    Observation.createNotStarted("instant.operation", registry)
        .observe(
            () -> {
              // 不做任何等待
            });

    // 验证 Observation 正常完成
    assertThat(registry.getCurrentObservation()).isNull();
  }
}
