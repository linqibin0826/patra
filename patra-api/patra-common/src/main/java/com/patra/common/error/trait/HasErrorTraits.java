package com.patra.common.error.trait;

import java.util.Set;

/**
 * 提供错误语义特征（ErrorTrait）的异常接口。
 *
 * <p>使错误解析算法可依据语义而非仅类名映射到合适的 HTTP 状态与错误响应。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HasErrorTraits {
    
    /**
     * 返回该异常的语义特征集合。
     *
     * <p>可包含多个特征以便于丰富分类处理。</p>
     */
    Set<ErrorTrait> getErrorTraits();
}
