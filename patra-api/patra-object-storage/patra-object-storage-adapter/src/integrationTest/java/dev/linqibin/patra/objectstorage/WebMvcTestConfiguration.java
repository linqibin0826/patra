package dev.linqibin.patra.objectstorage;

import dev.linqibin.starter.core.error.config.CoreErrorAutoConfiguration;
import dev.linqibin.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/// WebMvcTest 切片测试配置类。
///
/// 提供 @WebMvcTest 所需的最小 Spring Boot 配置。
/// 由于 adapter 模块遵循六边形架构，不包含 @SpringBootApplication 类，
/// 需要显式提供此配置类作为测试上下文的根配置。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @WebMvcTest 能够加载 Spring 上下文
/// - 通过 @EnableAutoConfiguration 加载通用自动配置
/// - 显式导入错误处理自动配置（@WebMvcTest 默认不加载）
///
/// 异常处理说明：
///
/// 验证异常由 GlobalRestExceptionHandler 处理，自动映射为 HTTP 422。
///
/// 注意：Controller 由 @WebMvcTest + @Import 显式指定，保持切片测试的隔离性。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({CoreErrorAutoConfiguration.class, WebErrorAutoConfiguration.class})
public class WebMvcTestConfiguration {
  // 空配置类，仅提供 @SpringBootConfiguration 标记
}
