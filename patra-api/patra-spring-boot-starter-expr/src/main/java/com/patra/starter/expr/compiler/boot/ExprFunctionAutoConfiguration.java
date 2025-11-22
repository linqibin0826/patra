package com.patra.starter.expr.compiler.boot;

import com.patra.starter.expr.compiler.function.DefaultFunctionRegistry;
import com.patra.starter.expr.compiler.function.FunctionRegistry;
import com.patra.starter.expr.compiler.function.PubmedDatetypeFunction;
import com.patra.starter.expr.compiler.function.RenderFunction;
import com.patra.starter.expr.compiler.transform.DefaultTransformRegistry;
import com.patra.starter.expr.compiler.transform.FilterJoinTransform;
import com.patra.starter.expr.compiler.transform.ListJoinTransform;
import com.patra.starter.expr.compiler.transform.ToExclusiveMinus1DTransform;
import com.patra.starter.expr.compiler.transform.TransformRegistry;
import com.patra.starter.expr.compiler.transform.ValueTransform;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// 表达式函数和变换注册表的自动配置。
/// 
/// 注册内置函数和变换：
/// 
/// - 函数：PUBMED_DATETYPE
///   - 变换：TO_EXCLUSIVE_MINUS_1D, LIST_JOIN, FILTER_JOIN
/// 
/// 自定义函数/变换的添加方式：
/// 
/// 参考：docs/expr/03-compiler-bridge-internals.md §3.3
/// 
/// @since 1.0.0
@AutoConfiguration
public class ExprFunctionAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ExprFunctionAutoConfiguration.class);

  /// 注册 PubMed 日期类型函数。
/// 
/// @return PUBMED_DATETYPE 函数实例
  @Bean
  @ConditionalOnMissingBean(name = "pubmedDatetypeFunction")
  public RenderFunction pubmedDatetypeFunction() {
    return new PubmedDatetypeFunction();
  }

  /// 注册排他转包含日期变换（将排他性日期转换为包含性日期）。
/// 
/// @return TO_EXCLUSIVE_MINUS_1D 变换实例
  @Bean
  @ConditionalOnMissingBean(name = "toExclusiveMinus1DTransform")
  public ValueTransform toExclusiveMinus1DTransform() {
    return new ToExclusiveMinus1DTransform();
  }

  /// 注册列表拼接变换（用于 MULTI 基准键）。
/// 
/// @return LIST_JOIN 变换实例
  @Bean
  @ConditionalOnMissingBean(name = "listJoinTransform")
  public ValueTransform listJoinTransform() {
    return new ListJoinTransform();
  }

  /// 注册过滤拼接变换（用于 MULTI 基准键）。
/// 
/// @return FILTER_JOIN 变换实例
  @Bean
  @ConditionalOnMissingBean(name = "filterJoinTransform")
  public ValueTransform filterJoinTransform() {
    return new FilterJoinTransform();
  }

  /// 创建函数注册表，包含所有可用的 RenderFunction Bean。
/// 
/// 可通过定义额外的 RenderFunction Bean 来添加自定义函数。
/// 
/// @param functions 上下文中所有 RenderFunction Bean 的列表
/// @return 不可变的函数注册表
  @Bean
  @ConditionalOnMissingBean
  public FunctionRegistry functionRegistry(List<RenderFunction> functions) {
    log.info("初始化函数注册表，包含 {} 个函数", functions.size());
    functions.forEach(fn -> log.debug("已注册函数：{}", fn.code()));
    return new DefaultFunctionRegistry(functions);
  }

  /// 创建变换注册表，包含所有可用的 ValueTransform Bean。
/// 
/// 可通过定义额外的 ValueTransform Bean 来添加自定义变换。
/// 
/// @param transforms 上下文中所有 ValueTransform Bean 的列表
/// @return 不可变的变换注册表
  @Bean
  @ConditionalOnMissingBean
  public TransformRegistry transformRegistry(List<ValueTransform> transforms) {
    log.info("初始化变换注册表，包含 {} 个变换", transforms.size());
    transforms.forEach(t -> log.debug("已注册变换：{}", t.code()));
    return new DefaultTransformRegistry(transforms);
  }
}
