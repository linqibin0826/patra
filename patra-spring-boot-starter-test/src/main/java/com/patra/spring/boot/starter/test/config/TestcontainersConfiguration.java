package com.patra.spring.boot.starter.test.config;

import com.patra.spring.boot.starter.test.container.MySQLTestContainer;
import com.patra.spring.boot.starter.test.container.NacosTestContainer;
import com.patra.spring.boot.starter.test.container.RedisTestContainer;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * TestContainers 自动配置
 *
 * <p>提供 MySQL, Redis, Nacos 容器的自动初始化,支持容器复用和性能优化。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动配置 MySQL 容器: 通过 @ServiceConnection 自动配置 DataSource</li>
 *   <li>自动配置 Redis 容器: 通过 @ServiceConnection 自动配置 RedisConnectionFactory</li>
 *   <li>自动配置 Nacos 容器: 需要手动配置 Nacos 属性 (使用 @DynamicPropertySource)</li>
 *   <li>容器复用: 启用 Reusable Containers 以加快测试速度</li>
 *   <li>支持 devtools 热重启: 使用 @RestartScope</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestcontainersConfiguration.class)
 * class MyIntegrationTest {
 *
 *     @Autowired
 *     private DataSource dataSource;
 *
 *     @Test
 *     void testDatabaseConnection() {
 *         assertThat(dataSource).isNotNull();
 *     }
 * }
 * }</pre>
 *
 * <h3>性能优化</h3>
 * <ul>
 *   <li>首次启动: 约 10-30 秒</li>
 *   <li>容器复用后: 约 1-2 秒</li>
 *   <li>总体提升: 约 90%</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * MySQL 容器配置
     *
     * <p>通过 @ServiceConnection 自动配置以下 Spring Boot 属性:</p>
     * <ul>
     *   <li>spring.datasource.url</li>
     *   <li>spring.datasource.username</li>
     *   <li>spring.datasource.password</li>
     *   <li>spring.datasource.driver-class-name</li>
     * </ul>
     *
     * @return MySQL 容器实例
     */
    @Bean
    @ServiceConnection
    @RestartScope
    public MySQLContainer<?> mysqlContainer() {
        return MySQLTestContainer.create();
    }

    /**
     * Redis 容器配置
     *
     * <p>通过 @ServiceConnection 自动配置以下 Spring Boot 属性:</p>
     * <ul>
     *   <li>spring.data.redis.host</li>
     *   <li>spring.data.redis.port</li>
     * </ul>
     *
     * <p><strong>重要提示</strong>: 使用 GenericContainer 时,@ServiceConnection 的 name 参数是必需的。</p>
     *
     * @return Redis 容器实例
     */
    @Bean
    @ServiceConnection(name = "redis")
    @RestartScope
    public GenericContainer<?> redisContainer() {
        return RedisTestContainer.create();
    }

    /**
     * Nacos 容器配置
     *
     * <p><strong>重要提示</strong>: Nacos 不支持 @ServiceConnection 自动配置,
     * 需要在测试类中使用 @DynamicPropertySource 手动配置属性。</p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * @DynamicPropertySource
     * static void registerNacosProperties(DynamicPropertyRegistry registry,
     *                                    @Autowired GenericContainer<?> nacos) {
     *     String serverAddr = nacos.getHost() + ":" + nacos.getMappedPort(8848);
     *     registry.add("spring.cloud.nacos.config.server-addr", () -> serverAddr);
     *     registry.add("spring.cloud.nacos.discovery.server-addr", () -> serverAddr);
     * }
     * }</pre>
     *
     * @return Nacos 容器实例
     */
    @Bean
    @RestartScope
    public GenericContainer<?> nacosContainer() {
        return NacosTestContainer.create();
    }
}
