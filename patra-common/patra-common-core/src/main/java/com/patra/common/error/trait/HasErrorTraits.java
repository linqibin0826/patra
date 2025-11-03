package com.patra.common.error.trait;

import java.util.Set;

/**
 * 暴露语义 {@link ErrorTrait} 的异常标记接口。
 *
 * <p>使错误解析管道在推导 HTTP 状态码和响应负载时能够依赖语义而不是类名启发式规则。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HasErrorTraits {

  /**
   * 返回与此异常关联的语义特征。
   *
   * @return 特征集合;可以提供多个特征以驱动细粒度分类
   */
  Set<ErrorTrait> getErrorTraits();
}
