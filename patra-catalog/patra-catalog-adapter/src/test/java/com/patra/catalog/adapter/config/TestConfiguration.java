package com.patra.catalog.adapter.config;

import com.patra.catalog.app.config.MeshImportConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/// 测试配置类。
///
/// 用于解决 @WebMvcTest 找不到 @SpringBootConfiguration 的问题。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @WebMvcTest 能够加载 Spring 上下文
///   - 提供测试所需的 Bean（如 MeshImportConfig）
///   - 限制组件扫描范围，避免加载不必要的依赖（如 Job、Listener 等）
///   - 通过 @EnableAutoConfiguration 自动加载 patra-starter-web 的异常处理器
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration // 自动加载 patra-starter-web 和 patra-starter-core 的配置
@ComponentScan(basePackages = "com.patra.catalog.adapter.rest") // 只扫描 REST 相关组件
public class TestConfiguration {

  /// MeSH 导入配置 Bean（测试用）。
  ///
  /// 用于 @WebMvcTest 切片测试，提供 StartImportAssembler 所需的依赖。
  ///
  /// @return MeshImportConfig 测试实例
  @Bean
  public MeshImportConfig meshImportConfig() {
    MeshImportConfig config = new MeshImportConfig();
    config.setSourceUrl(
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
    return config;
  }
}
