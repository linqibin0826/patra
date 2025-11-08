package com.patra.ingest.integration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * 标记慢速测试（需要 RocketMQ 容器）。
 *
 * <p>被此注解标记的测试类或方法会被归类为"slow"标签，可以通过 Maven Profile 控制是否执行。
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>依赖 RocketMQ Testcontainers 的集成测试
 *   <li>依赖 MySQL Testcontainers 的集成测试
 *   <li>E2E 测试（多组件协同）
 *   <li>执行时间超过 5 秒的测试
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @SlowTest
 * @SpringBootTest
 * class RocketMqOutboxPublisherIT {
 *     // 测试方法...
 * }
 * }</pre>
 *
 * <h3>Maven 使用</h3>
 *
 * <pre>{@code
 * # 快速测试（跳过 @SlowTest 标记的测试）
 * mvn test -Pfast-tests
 *
 * # 完整测试（包含所有测试）
 * mvn test -Pall-tests
 * # 或直接
 * mvn test
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 * @see Tag
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("slow")
public @interface SlowTest {}
