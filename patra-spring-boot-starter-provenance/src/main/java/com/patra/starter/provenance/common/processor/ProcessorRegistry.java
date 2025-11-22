package com.patra.starter.provenance.common.processor;

import com.patra.common.model.DataType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// DataProcessor注册表
/// 
/// ProcessorRegistry负责管理所有DataProcessor实例的注册和查找。
/// 
/// **核心功能**：
/// 
/// - 自动发现和注册DataProcessor（Spring依赖注入）
///   - 提供O(1)时间复杂度的Processor查找
///   - 类型安全的Processor获取
///   - 重复注册检测和警告
/// 
/// **设计理念**：
/// 
/// - 线程安全：使用ConcurrentHashMap保证并发安全
///   - 自动注册：Spring自动注入所有DataProcessor实现
///   - 灵活查找：提供getProcessor（抛异常）和findProcessor（返回Optional）两种方式
///   - 防御性编程：检测重复注册，处理null输入
/// 
/// **使用示例**：
/// 
/// ```java
/// // Spring自动注入
/// @Component
/// public class PubmedDataProvider implements ProvenanceDataProvider {
///     private final ProcessorRegistry processorRegistry;
/// 
///     public PubmedDataProvider(ProcessorRegistry processorRegistry) {
///         this.processorRegistry = processorRegistry;
/// 
///     public <T> ProviderResult<T> fetchData(...) {
///         // 查找Processor
///         DataProcessor<T> processor = processorRegistry.getProcessor(dataType);
/// 
///         // 委托处理
///         ProcessResult<T> result = processor.process(request, context);
///         return convertToProviderResult(result);
/// ```
/// 
/// **架构位置**：
/// 
/// ```
/// 
/// ProvenanceDataProvider (提供者)
///     └─ 查找 → ProcessorRegistry (注册表)
///                   └─ 返回 → DataProcessor<T> (处理器)
/// 
/// ```
/// 
/// @author Patra Architecture Team
/// @since 0.1.0
@Component
@Slf4j
public class ProcessorRegistry {

  /// Processor索引：DataType -> DataProcessor
/// 
/// 使用ConcurrentHashMap保证线程安全：
/// 
/// - 注册阶段：在构造函数中完成，无并发问题
///   - 查找阶段：多线程并发读取，ConcurrentHashMap保证安全
/// 
  private final Map<DataType, DataProcessor<?>> processors = new ConcurrentHashMap<>();

  /// 构造函数（Spring自动注入所有DataProcessor实现）
/// 
/// Spring会自动收集所有标注@Component的DataProcessor实现， 并注入到这个构造函数中。
/// 
/// **注入机制**：
/// 
/// ```java
/// // Spring会找到所有实现了DataProcessor接口的@Component
/// @Component
/// public class PubmedPublicationProcessor implements DataProcessor<CanonicalPublication> {
///     // ... 实现代码
/// 
/// // 自动注入到ProcessorRegistry
/// public ProcessorRegistry(List<DataProcessor<?>> discoveredProcessors) {
///     // discoveredProcessors包含所有Processor实例
/// ```
/// 
/// @param discoveredProcessors Spring发现的所有Processor实例
  public ProcessorRegistry(List<DataProcessor<?>> discoveredProcessors) {
    if (discoveredProcessors != null && !discoveredProcessors.isEmpty()) {
      discoveredProcessors.forEach(this::register);
      logRegistrationSummary();
    } else {
      log.warn("未发现任何DataProcessor实现");
    }
  }

  /// 注册单个Processor
/// 
/// **重复注册策略**：
/// 
/// - 检测：如果DataType已注册，记录警告日志
///   - 保留：保留第一个注册的Processor
///   - 忽略：忽略后续注册的Processor
///   - 不抛异常：避免启动失败
/// 
/// @param processor Processor实例
  private void register(DataProcessor<?> processor) {
    DataType dataType = processor.getDataType();

    // 检测重复注册
    if (processors.containsKey(dataType)) {
      log.warn(
          "检测到重复注册的Processor: type={}, existing={}, new={}",
          dataType,
          processors.get(dataType).getClass().getSimpleName(),
          processor.getClass().getSimpleName());
      log.warn("保留第一个注册的Processor，忽略后续注册");
      return;
    }

    processors.put(dataType, processor);
    log.debug("注册Processor: type={}, impl={}", dataType, processor.getClass().getSimpleName());
  }

  /// 获取指定数据类型的Processor
/// 
/// **类型安全注意事项**： 虽然使用了@SuppressWarnings("unchecked")，但类型安全由以下机制保证：
/// 
/// - DataType.getDataClass()定义了预期的数据类型
///   - DataProcessor实现时需确保返回类型与DataType一致
///   - 运行时通过DataType验证类型一致性
/// 
/// **使用场景**：
/// 
/// - 确定Processor存在的情况（如配置明确指定支持的类型）
///   - 允许抛出异常中断流程
/// 
/// @param <T> 数据类型
/// @param dataType 数据类型标识
/// @return Processor实例
/// @throws ProcessorNotFoundException 如果Processor不存在
  @SuppressWarnings("unchecked")
  public <T> DataProcessor<T> getProcessor(DataType dataType) {
    DataProcessor<?> processor = processors.get(dataType);

    if (processor == null) {
      throw new ProcessorNotFoundException(String.format("未找到DataType的Processor: %s", dataType));
    }

    return (DataProcessor<T>) processor;
  }

  /// 查找指定数据类型的Processor（返回Optional）
/// 
/// **使用场景**：
/// 
/// - 不确定Processor是否存在的情况
///   - 需要优雅处理Processor不存在的情况
///   - 避免异常处理的函数式编程风格
/// 
/// **使用示例**：
/// 
/// ```java
/// // 函数式风格处理
/// Optional<DataProcessor<?>> processor = registry.findProcessor(dataType);
/// processor.ifPresent(p -> {
///     // 使用Processor);
/// 
/// // 提供默认处理
/// DataProcessor<?> processor = registry.findProcessor(dataType)
///     .orElseGet(() -> new DefaultProcessor());
/// ```
/// 
/// @param dataType 数据类型标识
/// @return Processor实例（如果存在）
  public Optional<DataProcessor<?>> findProcessor(DataType dataType) {
    return Optional.ofNullable(processors.get(dataType));
  }

  /// 判断是否支持指定的数据类型
/// 
/// **使用场景**：
/// 
/// - 在获取Processor前先检查是否支持
///   - 过滤支持的数据类型
///   - 配置校验
/// 
/// @param dataType 数据类型
/// @return 如果存在对应的Processor则返回true
  public boolean supports(DataType dataType) {
    return processors.containsKey(dataType);
  }

  /// 获取所有支持的数据类型
/// 
/// **返回值说明**：
/// 
/// - 返回不可变集合（Set.copyOf）
///   - 避免外部修改内部状态
///   - 线程安全
/// 
/// **使用场景**：
/// 
/// - 展示系统支持的所有数据类型
///   - 配置校验
///   - 生成API文档
/// 
/// @return 数据类型集合（不可变）
  public Set<DataType> getSupportedTypes() {
    return Set.copyOf(processors.keySet());
  }

  /// 记录注册统计信息
/// 
/// **日志输出示例**：
/// 
/// ```
/// 
/// INFO  Processor注册完成: 共注册2个Processor [PUBLICATION, JOURNAL]
/// DEBUG   - PUBLICATION: com.patra.starter.provenance.processor.PublicationProcessor
/// DEBUG   - JOURNAL: com.patra.starter.provenance.processor.JournalProcessor
/// 
/// ```
  private void logRegistrationSummary() {
    int count = processors.size();
    Set<DataType> types = processors.keySet();

    log.info("Processor注册完成: 共注册{}个Processor {}", count, types);

    // 详细日志
    processors.forEach(
        (type, processor) -> {
          log.debug("  - {}: {}", type, processor.getClass().getName());
        });
  }
}
