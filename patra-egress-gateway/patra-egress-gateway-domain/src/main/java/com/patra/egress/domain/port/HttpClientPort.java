package com.patra.egress.domain.port;

import com.patra.egress.domain.model.vo.HttpRequest;
import com.patra.egress.domain.model.vo.HttpResponse;
import com.patra.egress.domain.model.vo.ResilienceConfig;

/**
 * HTTP客户端端口接口
 * 定义外部HTTP调用的抽象接口
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface HttpClientPort {
    
    /**
     * 执行HTTP调用
     * 
     * @param request HTTP请求
     * @param config 弹性配置（包含超时等设置）
     * @return HTTP响应
     */
    HttpResponse call(HttpRequest request, ResilienceConfig config);
}
