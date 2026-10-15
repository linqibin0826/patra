package dev.patra.ingest;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra 采集服务 Spring Boot 启动类。
///
/// Patra 医学出版物数据平台的数据采集微服务,负责从 PubMed、EMBASE 等外部数据源采集医学出版物数据。
///
/// 核心功能:
///
/// - 定时调度数据采集任务(通过 XXL-Job)
///   - 执行数据采集和初步解析
///   - 管理采集计划和任务生命周期
///   - 发布任务就绪事件到消息队列
///   - 实现事务性发件箱模式确保消息可靠投递
///
/// 技术栈集成:
///
/// - Spring Cloud - 微服务基础设施和服务发现
/// - HTTP Interface - 声明式 HTTP 客户端（通过 `patra-spring-boot-starter-http-interface`）
/// - RocketMQ - 消息队列（通过 RocketMQ 官方 Spring Boot Starter）
/// - XXL-Job - 分布式任务调度
/// - Spring Data JPA - 数据持久化
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-ingest.yaml`），由启动器导入。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication
public class PatraIngestApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-ingest`。
  ///
  /// @param args 命令行参数
  static void main(String[] args) {
    PatraApplication.run("patra-ingest", PatraIngestApplication.class, args);
  }
}
