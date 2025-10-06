package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.ResilienceConfig;

/**
 * 配置端口接口
 * 定义配置加载的抽象接口，支持从不同配置源加载
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ConfigPort {
    
    /**
     * 加载系统级默认配置
     * 
     * @return 系统默认弹性配置
     */
    ResilienceConfig loadSystemDefaultConfig();
    
    /**
     * 加载系统级最大配置
     * 
     * @return 系统最大弹性配置
     */
    ResilienceConfig loadSystemMaxConfig();
}
