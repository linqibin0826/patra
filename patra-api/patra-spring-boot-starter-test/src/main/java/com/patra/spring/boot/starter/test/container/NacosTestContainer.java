package com.patra.spring.boot.starter.test.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Nacos 测试容器
 *
 * <p>提供 Nacos 2.3.0 测试容器的配置和管理,支持单机模式和容器复用。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>单机模式: 适合测试环境,启动快</li>
 *   <li>容器复用: 启用 Reusable Containers 以加快测试速度</li>
 *   <li>健康检查: 自动等待 Nacos 启动完成</li>
 *   <li>多端口支持: HTTP (8848) + gRPC (9848, 9849)</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Bean
 * @ServiceConnection(name = "nacos")
 * public GenericContainer<?> nacosContainer() {
 *     return NacosTestContainer.create();
 * }
 *
 * @DynamicPropertySource
 * static void registerNacosProperties(DynamicPropertyRegistry registry,
 *                                    @Autowired GenericContainer<?> nacos) {
 *     String serverAddr = nacos.getHost() + ":" + nacos.getMappedPort(8848);
 *     registry.add("spring.cloud.nacos.config.server-addr", () -> serverAddr);
 *     registry.add("spring.cloud.nacos.discovery.server-addr", () -> serverAddr);
 * }
 * }</pre>
 *
 * <h3>重要提示</h3>
 * <ul>
 *   <li>Nacos 容器启动较慢 (约 30-60 秒),已设置 2 分钟超时</li>
 *   <li>需要手动配置 Nacos 属性 (使用 @DynamicPropertySource)</li>
 *   <li>建议启用容器复用以提升本地开发效率</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public final class NacosTestContainer {

    /**
     * 默认 Nacos 镜像
     */
    public static final String DEFAULT_IMAGE = "nacos/nacos-server:v2.3.0";

    /**
     * HTTP 端口
     */
    public static final int HTTP_PORT = 8848;

    /**
     * gRPC 端口 1
     */
    public static final int GRPC_PORT_1 = 9848;

    /**
     * gRPC 端口 2
     */
    public static final int GRPC_PORT_2 = 9849;

    /**
     * 默认启动超时时间
     */
    public static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);

    private NacosTestContainer() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 创建 Nacos 容器实例 (单机模式)
     *
     * <p>配置说明:</p>
     * <ul>
     *   <li>镜像: nacos/nacos-server:v2.3.0</li>
     *   <li>运行模式: standalone (单机)</li>
     *   <li>容器复用: 启用</li>
     *   <li>暴露端口: 8848 (HTTP), 9848, 9849 (gRPC)</li>
     *   <li>启动超时: 2 分钟</li>
     *   <li>JVM 内存: 256m (最小/最大)</li>
     * </ul>
     *
     * @return Nacos 容器实例
     */
    public static GenericContainer<?> create() {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(HTTP_PORT, GRPC_PORT_1, GRPC_PORT_2)
            .withEnv("MODE", "standalone")
            .withEnv("PREFER_HOST_MODE", "hostname")
            .withEnv("JVM_XMS", "256m")
            .withEnv("JVM_XMX", "256m")
            .waitingFor(Wait.forHttp("/nacos/")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT))
            .withReuse(true);
    }

    /**
     * 创建带自定义超时时间的 Nacos 容器
     *
     * @param startupTimeout 启动超时时间
     * @return Nacos 容器实例
     */
    public static GenericContainer<?> create(Duration startupTimeout) {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(HTTP_PORT, GRPC_PORT_1, GRPC_PORT_2)
            .withEnv("MODE", "standalone")
            .withEnv("PREFER_HOST_MODE", "hostname")
            .withEnv("JVM_XMS", "256m")
            .withEnv("JVM_XMX", "256m")
            .waitingFor(Wait.forHttp("/nacos/")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(startupTimeout))
            .withReuse(true);
    }
}
