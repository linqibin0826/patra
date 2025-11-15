package com.patra.ingest.infra.mapper;

import com.patra.common.enums.ProvenanceCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据源参数映射器注册表。
 *
 * <p>负责管理所有 {@link ProviderParameterMapper} 实现，并提供基于 {@link ProvenanceCode} 的路由功能。
 *
 * <p><strong>设计原则</strong>：
 *
 * <ul>
 *   <li>自动注册：通过 Spring 构造器注入自动收集所有 Mapper 实现
 *   <li>开闭原则：新增数据源只需实现 {@link ProviderParameterMapper} 接口，零修改
 *   <li>类型安全：使用枚举 {@link ProvenanceCode} 作为路由键
 * </ul>
 *
 * <p><strong>使用示例</strong>：
 *
 * <pre>{@code
 * // 获取 PubMed 的参数映射器
 * ProviderParameterMapper mapper = registry.getMapper(ProvenanceCode.PUBMED);
 * JsonNode params = mapper.mapParameters(batch, baseParams, metadata);
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 * @see ProviderParameterMapper
 */
@Component
@Slf4j
public class ProviderParameterMapperRegistry {

  private final Map<ProvenanceCode, ProviderParameterMapper> mappers;

  /**
   * 构造函数：自动注册所有参数映射器。
   *
   * <p>Spring 会自动注入所有 {@link ProviderParameterMapper} 实现， 构造函数将其转换为以 {@link ProvenanceCode} 为键的
   * Map。
   *
   * <p><strong>设计要点</strong>：
   *
   * <ul>
   *   <li>消除硬编码类型列表，通过 {@link ProviderParameterMapper#getSupportedProvenance()} 自动发现
   *   <li>完全符合开闭原则，新增数据源零修改
   *   <li>启动时检测重复注册，快速失败
   * </ul>
   *
   * @param mapperList 所有参数映射器实现（由 Spring 自动注入）
   */
  public ProviderParameterMapperRegistry(List<ProviderParameterMapper> mapperList) {
    this.mappers = buildMapperMap(mapperList);
    log.info("参数映射器注册完成: 共注册 {} 个映射器 - {}", mappers.size(), mappers.keySet());
  }

  /**
   * 构建映射器 Map。
   *
   * <p>遍历所有 Mapper 实现，根据其 {@link ProviderParameterMapper#getSupportedProvenance()} 返回值注册到 Map 中。
   *
   * @param mapperList Mapper 实现列表
   * @return 以 ProvenanceCode 为键的 Mapper Map
   * @throws IllegalStateException 如果检测到重复的 ProvenanceCode
   */
  private Map<ProvenanceCode, ProviderParameterMapper> buildMapperMap(
      List<ProviderParameterMapper> mapperList) {

    Map<ProvenanceCode, ProviderParameterMapper> map = new HashMap<>();

    for (ProviderParameterMapper mapper : mapperList) {
      ProvenanceCode provenanceCode = mapper.getSupportedProvenance();

      if (provenanceCode == null) {
        log.warn("映射器 {} 返回 null 的 provenanceCode，跳过注册", mapper.getClass().getSimpleName());
        continue;
      }

      if (map.containsKey(provenanceCode)) {
        String existingMapper = map.get(provenanceCode).getClass().getSimpleName();
        String newMapper = mapper.getClass().getSimpleName();

        log.error(
            "检测到重复的参数映射器: provenanceCode={}, 已有={}, 新增={}",
            provenanceCode,
            existingMapper,
            newMapper);

        throw new IllegalStateException(
            String.format(
                "重复的参数映射器: provenanceCode=%s, existing=%s, new=%s",
                provenanceCode, existingMapper, newMapper));
      }

      map.put(provenanceCode, mapper);
      log.debug("注册参数映射器: {} -> {}", provenanceCode, mapper.getClass().getSimpleName());
    }

    if (map.isEmpty()) {
      log.warn("未发现任何参数映射器，请检查 Spring 配置");
    }

    return map;
  }

  /**
   * 获取指定数据源的参数映射器。
   *
   * @param provenanceCode 数据源代码
   * @return 参数映射器
   * @throws IllegalStateException 如果找不到对应的映射器
   */
  public ProviderParameterMapper getMapper(ProvenanceCode provenanceCode) {
    ProviderParameterMapper mapper = mappers.get(provenanceCode);

    if (mapper == null) {
      throw new IllegalStateException(
          String.format(
              "未找到参数映射器: provenanceCode=%s, 可用的映射器: %s", provenanceCode, mappers.keySet()));
    }

    return mapper;
  }

  /**
   * 检查是否支持指定的数据源。
   *
   * @param provenanceCode 数据源代码
   * @return 如果支持返回 true
   */
  public boolean supports(ProvenanceCode provenanceCode) {
    return mappers.containsKey(provenanceCode);
  }
}
