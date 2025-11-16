package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Patra 采集服务 Spring Boot 启动类。
 *
 * <p>Patra 医学出版物数据平台的数据采集微服务,负责从 PubMed、EMBASE 等外部数据源采集医学出版物数据。
 *
 * <p>核心功能:
 *
 * <ul>
 *   <li>定时调度数据采集任务(通过 XXL-Job)
 *   <li>执行数据采集和初步解析
 *   <li>管理采集计划和任务生命周期
 *   <li>发布任务就绪事件到消息队列
 *   <li>实现事务性发件箱模式确保消息可靠投递
 * </ul>
 *
 * <p>技术栈集成:
 *
 * <ul>
 *   <li>Spring Cloud - 微服务基础设施和服务发现
 *   <li>Feign 客户端 - 通过 {@code patra-spring-cloud-starter-feign} 自动扫描 {@code
 *       com.patra.*.api.rpc.client} 包下的接口
 *   <li>RocketMQ - 消息队列(通过 RocketMQ 官方 Spring Boot Starter)
 *   <li>XXL-Job - 分布式任务调度
 *   <li>MyBatis-Plus - 数据持久化
 * </ul>
 *
 * <p>启动配置: 未指定 profile 时默认使用 'dev' 环境配置。
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootApplication
public class PatraIngestApplication {

  public static void main(String[] args) {
    // 当未配置显式配置文件时,默认使用 'dev' 配置
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIngestApplication.class, args);
  }
}
