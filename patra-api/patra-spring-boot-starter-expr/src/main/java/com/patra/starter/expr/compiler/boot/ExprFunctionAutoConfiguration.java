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

/**
 * Auto-configuration for expression function and transform registries.
 *
 * <p>Registers built-in functions and transforms: - Functions: PUBMED_DATETYPE - Transforms:
 * TO_EXCLUSIVE_MINUS_1D, LIST_JOIN, FILTER_JOIN
 *
 * <p>Custom functions/transforms can be added by: 1. Defining beans of type {@link RenderFunction}
 * or {@link ValueTransform} 2. Providing a custom {@link FunctionRegistry} or {@link
 * TransformRegistry} bean
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class ExprFunctionAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ExprFunctionAutoConfiguration.class);

  /**
   * Registers the PubMed datetype function.
   *
   * @return PUBMED_DATETYPE function instance
   */
  @Bean
  @ConditionalOnMissingBean(name = "pubmedDatetypeFunction")
  public RenderFunction pubmedDatetypeFunction() {
    return new PubmedDatetypeFunction();
  }

  /**
   * Registers the exclusive-to-inclusive date transform.
   *
   * @return TO_EXCLUSIVE_MINUS_1D transform instance
   */
  @Bean
  @ConditionalOnMissingBean(name = "toExclusiveMinus1DTransform")
  public ValueTransform toExclusiveMinus1DTransform() {
    return new ToExclusiveMinus1DTransform();
  }

  /**
   * Registers the list join transform for MULTI std_keys.
   *
   * @return LIST_JOIN transform instance
   */
  @Bean
  @ConditionalOnMissingBean(name = "listJoinTransform")
  public ValueTransform listJoinTransform() {
    return new ListJoinTransform();
  }

  /**
   * Registers the filter join transform for MULTI std_keys.
   *
   * @return FILTER_JOIN transform instance
   */
  @Bean
  @ConditionalOnMissingBean(name = "filterJoinTransform")
  public ValueTransform filterJoinTransform() {
    return new FilterJoinTransform();
  }

  /**
   * Creates the function registry with all available RenderFunction beans.
   *
   * <p>Custom functions can be added by defining additional RenderFunction beans.
   *
   * @param functions list of all RenderFunction beans in the context
   * @return immutable function registry
   */
  @Bean
  @ConditionalOnMissingBean
  public FunctionRegistry functionRegistry(List<RenderFunction> functions) {
    log.info("Initializing FunctionRegistry with {} functions", functions.size());
    functions.forEach(fn -> log.debug("Registered function: {}", fn.code()));
    return new DefaultFunctionRegistry(functions);
  }

  /**
   * Creates the transform registry with all available ValueTransform beans.
   *
   * <p>Custom transforms can be added by defining additional ValueTransform beans.
   *
   * @param transforms list of all ValueTransform beans in the context
   * @return immutable transform registry
   */
  @Bean
  @ConditionalOnMissingBean
  public TransformRegistry transformRegistry(List<ValueTransform> transforms) {
    log.info("Initializing TransformRegistry with {} transforms", transforms.size());
    transforms.forEach(t -> log.debug("Registered transform: {}", t.code()));
    return new DefaultTransformRegistry(transforms);
  }
}
