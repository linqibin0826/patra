package dev.linqibin.patra.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra 目录管理服务启动类。
///
/// Catalog 是 Patra 平台的目录管理微服务，负责管理医学文献的分类体系、主题词表、作者、机构、期刊等核心目录数据。
///
/// 主要功能:
///
/// - MeSH（Medical Subject Headings）医学主题词表数据模型
///   - 主题词（Descriptor）和限定词（Qualifier）
///   - 树形编号（TreeNumber）层次结构
///   - 入口术语（EntryTerm）同义词管理
///   - 概念（Concept）关系维护
/// - 期刊（Venue）目录和影响因子维护
/// - 作者（Author）和机构（Organization）信息管理
/// - 文献分类和标引服务
///
/// 技术栈集成:
///
/// - Spring Cloud - 微服务基础设施和服务发现
/// - HTTP Interface - 声明式 HTTP 客户端（通过 `patra-spring-boot-starter-http-interface`）
/// - Spring Data JPA + Hibernate - 数据持久化
/// - RocketMQ - 消息队列（事件发布）
///
/// 启动配置: 未指定 profile 时默认使用 'dev' 环境配置。
///
/// @author linqibin
/// @since 0.2.0
@SpringBootApplication
public class PatraCatalogApplication {

  /// 应用程序入口点,启动 Spring Boot 应用。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    // 当未配置显式配置文件时,默认使用 'dev' 配置
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraCatalogApplication.class, args);
  }
}
