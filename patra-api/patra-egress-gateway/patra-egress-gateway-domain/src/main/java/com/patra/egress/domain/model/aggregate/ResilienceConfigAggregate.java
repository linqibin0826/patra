package com.patra.egress.domain.model.aggregate;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.ConfigPort;

/**
 * 弹性配置聚合根
 * 管理弹性配置的加载、合并和校验
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class ResilienceConfigAggregate {
    
    private final ResilienceConfig systemDefaultConfig;
    private final ResilienceConfig systemMaxConfig;
    
    /**
     * 私有构造函数，通过静态工厂方法创建
     * 
     * @param systemDefaultConfig 系统默认配置
     * @param systemMaxConfig 系统最大配置
     */
    private ResilienceConfigAggregate(
        ResilienceConfig systemDefaultConfig,
        ResilienceConfig systemMaxConfig
    ) {
        this.systemDefaultConfig = systemDefaultConfig;
        this.systemMaxConfig = systemMaxConfig;
    }
    
    /**
     * 加载系统级配置（静态工厂方法）
     * 
     * @param configPort 配置端口
     * @return 弹性配置聚合根
     */
    public static ResilienceConfigAggregate loadSystemConfig(ConfigPort configPort) {
        ResilienceConfig defaultConfig = configPort.loadSystemDefaultConfig();
        ResilienceConfig maxConfig = configPort.loadSystemMaxConfig();
        
        // 校验系统配置
        defaultConfig.validate();
        maxConfig.validate();
        
        return new ResilienceConfigAggregate(defaultConfig, maxConfig);
    }
    
    /**
     * 合并业务方配置（不超过系统最大值）
     * 
     * @param callerConfig 业务方传递的配置，可以为null
     * @return 合并后的配置
     */
    public ResilienceConfig mergeWithCallerConfig(ResilienceConfig callerConfig) {
        // 如果业务方未传递配置，使用系统默认配置
        if (callerConfig == null) {
            return systemDefaultConfig;
        }
        
        // 校验业务方配置
        callerConfig.validate();
        
        // 合并配置，确保不超过系统最大值
        return callerConfig.mergeWithMax(systemMaxConfig);
    }
    
    /**
     * 校验配置有效性
     * 
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        systemDefaultConfig.validate();
        systemMaxConfig.validate();
    }
    
    /**
     * 获取系统默认配置
     * 
     * @return 系统默认配置
     */
    public ResilienceConfig getSystemDefaultConfig() {
        return systemDefaultConfig;
    }
    
    /**
     * 获取系统最大配置
     * 
     * @return 系统最大配置
     */
    public ResilienceConfig getSystemMaxConfig() {
        return systemMaxConfig;
    }
}
