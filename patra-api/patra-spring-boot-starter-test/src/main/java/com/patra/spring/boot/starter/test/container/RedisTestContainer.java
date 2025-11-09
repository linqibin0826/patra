package com.patra.spring.boot.starter.test.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 测试容器
 *
 * <p>提供 Redis 7-alpine 测试容器的配置和管理,支持容器复用。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>轻量级: 使用 alpine 镜像,体积小启动快</li>
 *   <li>容器复用: 启用 Reusable Containers 以加快测试速度</li>
 *   <li>自动配置: 通过 @ServiceConnection 自动配置 Spring Data Redis</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Bean
 * @ServiceConnection(name = "redis")
 * public GenericContainer<?> redisContainer() {
 *     return RedisTestContainer.create();
 * }
 * }</pre>
 *
 * <h3>重要提示</h3>
 * <p>使用 GenericContainer 时,@ServiceConnection 的 name 参数是必需的,
 * 否则 Spring Boot 无法识别容器类型。</p>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class RedisTestContainer {

    /**
     * 默认 Redis 镜像
     */
    public static final String DEFAULT_IMAGE = "redis:7-alpine";

    /**
     * 默认暴露端口
     */
    public static final int DEFAULT_PORT = 6379;

    private RedisTestContainer() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 创建 Redis 容器实例
     *
     * <p>配置说明:</p>
     * <ul>
     *   <li>镜像: redis:7-alpine</li>
     *   <li>容器复用: 启用</li>
     *   <li>暴露端口: 6379</li>
     * </ul>
     *
     * @return Redis 容器实例
     */
    public static GenericContainer<?> create() {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(DEFAULT_PORT)
            .withReuse(true);
    }

    /**
     * 创建带自定义端口的 Redis 容器
     *
     * @param port 暴露端口
     * @return Redis 容器实例
     */
    public static GenericContainer<?> create(int port) {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(port)
            .withReuse(true);
    }
}
