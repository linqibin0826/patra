package com.patra.ingest.app.config;

import org.springframework.context.annotation.Configuration;

/**
 * Ingest 应用层配置类
 *
 * <p>配置作用域: 应用层核心组件的配置
 *
 * <p>主要配置项:
 *
 * <ul>
 *   <li>应用层组件自动扫描(@Component/@Service)
 *   <li>编排器(Orchestrator)、事件处理器(EventHandler)、用例(UseCase)等组件的注册
 * </ul>
 *
 * <p>注意: 组件扫描会自动注册标注了 @Component/@Service 的实现类,无需在此显式定义 Bean
 *
 * @author linqibin
 * @since 0.1.0
 */
@Configuration
public class IngestAppConfig {

  // 组件扫描会自动注册 @Component/@Service 实现类,无需在此显式定义 Bean

}
