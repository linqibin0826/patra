package com.patra.spring.boot.starter.test.autoconfigure;

import com.patra.spring.boot.starter.test.config.TestcontainersConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

/**
 * TestContainers 自动配置类
 *
 * <p>在 TestContainers 类路径存在时,自动导入 TestcontainersConfiguration。</p>
 *
 * <h3>自动配置条件</h3>
 * <ul>
 *   <li>@ConditionalOnClass(GenericContainer.class): TestContainers 在类路径中</li>
 * </ul>
 *
 * <h3>配置内容</h3>
 * <ul>
 *   <li>导入 TestcontainersConfiguration</li>
 *   <li>提供 MySQL, Redis, Nacos 容器的 Bean 定义</li>
 * </ul>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(GenericContainer.class)
@Import(TestcontainersConfiguration.class)
public class TestContainersAutoConfiguration {

    // 此类仅用于自动配置导入,无需额外方法
}
