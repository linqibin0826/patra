package com.patra.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Patra Registry 服务启动类。
 *
 * <p>Registry 是 Patra 平台的核心配置服务,作为 Provenance、表达式、字典和元数据的单一事实来源(SSOT)。
 *
 * <p>主要功能:
 *
 * <ul>
 *   <li>管理数据来源(Provenance)配置和元数据
 *   <li>存储和管理表达式模板(ExprSnapshot)及其渲染规则
 *   <li>提供平台级字典和枚举值查询
 *   <li>支持多版本配置管理
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootApplication
public class PatraRegistryApplication {

  public static void main(String[] args) {
    SpringApplication.run(PatraRegistryApplication.class, args);
  }
}
