package com.patra.registry.domain.model.aggregate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.model.event.LiteratureProvenanceEvents;
import com.patra.registry.domain.model.vo.ApiParamMapping;
import com.patra.registry.domain.model.vo.QueryRenderRule;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文献数据源聚合根（Aggregate Root）。
 *
 * <p>职责：
 * - 作为一致性边界，封装数据源的配置、查询能力、渲染规则与参数映射；
 * - 守护不变式（Invariants）：名称、代码一致性；配置/能力与聚合的 {@link #code} 必须一致；规则/映射去重与有效性；
 * - 对外暴露意图化行为（改名、替换配置、替换规则/映射、登记备注）；
 * - 产生领域事件，供上层应用层转译并发布。
 *
 * <p>说明：
 * - 使用 Hutool 的 Assert/StrUtil/CollUtil 做参数与不变式校验；
 * - 尽量保持可变聚合（非 @Value 不可变），以承载状态流转；
 * - 领域事件采用“拉取”模型：行为内登记，外部通过 {@link #pullDomainEvents()} 获取并清空。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@ToString
@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
public class Provenance {

    /**
     * 聚合根ID（技术键）
     */
    private Long id;

    /**
     * 数据源名称；人类可读名称（如 "PubMed"）
     */
    private String name;

    /**
     * 数据源代码；业务键（如 pubmed/epmc/openalex/crossref）
     */
    private ProvenanceCode code;

    /**
     * 记录备注（审计信息）
     */
    @Builder.Default
    private List<RecordRemark> recordRemarks = new ArrayList<>();

    /**
     * 乐观锁版本号（由基础设施在持久化层维护）
     */
    private Long version;

    /**
     * 数据源配置（1:1）
     */
    private LiteratureProvenanceConfig config;

    /**
     * 查询能力（1:1）
     */
    private QueryCapability queryCapability;

    /**
     * API 参数映射（1:N 值对象）
     */
    @Builder.Default
    private List<ApiParamMapping> apiParamMappings = new ArrayList<>();

    /**
     * 查询渲染规则（1:N 值对象）
     */
    @Builder.Default
    private List<QueryRenderRule> queryRenderRules = new ArrayList<>();

    /**
     * 聚合内登记的领域事件（拉取后清空）。
     * 不参与持久化；由应用层读取并发布为 AppEvent/IntegrationEvent。
     */
    @Builder.Default
    private transient List<Object> domainEvents = new ArrayList<>();

    // ------------------------- 工厂方法 -------------------------

    /**
     * 工厂：创建有效的新聚合，并登记“已创建”事件。
     */
    public static Provenance create(Long id,
                                    ProvenanceCode code,
                                    String name,
                                    LiteratureProvenanceConfig config,
                                    QueryCapability capability,
                                    List<ApiParamMapping> mappings,
                                    List<QueryRenderRule> rules,
                                    String operator) {
        Assert.notNull(code, "code is required");
        Assert.isFalse(StrUtil.isBlank(name), "name is blank");
        Provenance agg = Provenance.builder()
                .id(id)
                .code(code)
                .name(StrUtil.trim(name))
                .config(config)
                .queryCapability(capability)
                .apiParamMappings(CollUtil.isEmpty(mappings) ? new ArrayList<>() : new ArrayList<>(mappings))
                .queryRenderRules(CollUtil.isEmpty(rules) ? new ArrayList<>() : new ArrayList<>(rules))
                .build();
        agg.validateInvariants();
        agg.appendRemark("created", operator);
        agg.registerEvent(new LiteratureProvenanceEvents.LiteratureProvenanceCreated(
                agg.id, agg.code.getCode(), agg.name, LocalDateTime.now()
        ));
        return agg;
    }

    // ------------------------- 行为（Behavior） -------------------------

    /**
     * 改名（幂等）
     */
    public void rename(String newName, String operator) {
        Assert.isFalse(StrUtil.isBlank(newName), "newName is blank");
        String normalized = StrUtil.trim(newName);
        if (StrUtil.equals(this.name, normalized)) {
            return; // 幂等：未变化则不触发事件
        }
        this.name = normalized;
    this.appendRemark("renamed to '" + normalized + "'", operator);
    this.registerUpdated();
    }


    /**
     * 替换配置（需与聚合 code 一致）
     */
    public void replaceConfig(LiteratureProvenanceConfig newConfig, String operator) {
        Assert.notNull(newConfig, "config is required");
        Assert.isTrue(newConfig.getProvenanceCode() == this.code, "config.code mismatch aggregate.code");
        newConfig.validate();
        this.config = newConfig;
        this.appendRemark("config replaced", operator);
        this.registerEvent(new LiteratureProvenanceEvents.LiteratureProvenanceConfigUpdated(
                this.id, this.code.getCode(), "full", LocalDateTime.now()
        ));
    }

    /**
     * 替换查询能力（需与聚合 code 一致）
     */
    public void replaceCapability(QueryCapability capability, String operator) {
        Assert.notNull(capability, "capability is required");
        Assert.isTrue(capability.getProvenanceCode() == this.code, "capability.code mismatch aggregate.code");
        capability.validate();
        this.queryCapability = capability;
        this.appendRemark("capability replaced", operator);
        this.registerEvent(new LiteratureProvenanceEvents.LiteratureProvenanceUpdated(
                this.id, this.code.getCode(), this.name, this.version, LocalDateTime.now()
        ));
    }

    /**
     * 替换 API 参数映射并做去重校验：按 (operation,stdKey) 保证唯一。
     */
    public void replaceApiParamMappings(List<ApiParamMapping> mappings, String operator) {
    List<ApiParamMapping> safe = toSafeMutableList(mappings);
    validateMappings(safe);
    this.apiParamMappings = safe;
        this.appendRemark("apiParamMappings replaced: " + safe.size(), operator);
    this.registerUpdated();
    }

    /**
     * 替换查询渲染规则并做去重与必要字段校验。
     * 唯一键：fieldKey + op + matchType? + negated? + valueType?
     */
    public void replaceQueryRenderRules(List<QueryRenderRule> rules, String operator) {
    List<QueryRenderRule> safe = toSafeMutableList(rules);
    validateRules(safe);
    this.queryRenderRules = safe;
        this.appendRemark("queryRenderRules replaced: " + safe.size(), operator);
    this.registerUpdated();
    }

    /**
     * 新增一条审计备注（幂等对同内容不强制）
     */
    public void appendRemark(String note, String by) {
        String n = StrUtil.nullToDefault(StrUtil.trim(note), "");
        String who = StrUtil.nullToDefault(StrUtil.trim(by), "system");
        this.recordRemarks.add(RecordRemark.builder()
                .time(LocalDateTime.now())
                .by(who)
                .note(n)
                .build());
    }

    // ------------------------- 不变式与事件 -------------------------

    /**
     * 聚合级不变式校验（在构造与关键状态变更前后调用）。
     */
    public void validateInvariants() {
        Assert.notNull(this.code, "code is required");
        Assert.isFalse(StrUtil.isBlank(this.name), "name is blank");
        if (this.config != null) {
            Assert.isTrue(this.config.getProvenanceCode() == this.code, "config.code mismatch aggregate.code");
            this.config.validate();
        }
        if (this.queryCapability != null) {
            Assert.isTrue(this.queryCapability.getProvenanceCode() == this.code, "capability.code mismatch aggregate.code");
            this.queryCapability.validate();
        }
        // 校验映射与规则（纯校验，无副作用）
        validateMappings(this.apiParamMappings);
        validateRules(this.queryRenderRules);
    }

    /**
     * 登记领域事件
     */
    private void registerEvent(Object event) {
        this.domainEvents.add(event);
    }

    /**
     * 拉取并清空领域事件。
     *
     * @return 事件列表（新列表，非内部引用）
     */
    public List<Object> pullDomainEvents() {
        List<Object> copy = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return copy;
    }

    // ------------------------- 私有校验辅助 -------------------------

    private static void validateMappings(List<ApiParamMapping> mappings) {
        List<ApiParamMapping> safe = CollUtil.isEmpty(mappings) ? List.of() : mappings;
        for (ApiParamMapping m : safe) {
            Assert.isFalse(StrUtil.isBlank(m.getOperation()), "mapping.operation blank");
            Assert.isFalse(StrUtil.isBlank(m.getStdKey()), "mapping.stdKey blank");
            Assert.isFalse(StrUtil.isBlank(m.getProviderParam()), "mapping.providerParam blank");
        }
        Map<String, Long> dup = safe.stream()
                .collect(Collectors.groupingBy(m -> m.getOperation() + "|" + m.getStdKey(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Assert.isTrue(dup.isEmpty(), "duplicate mapping keys: {}", dup.keySet());
    }

    private static void validateRules(List<QueryRenderRule> rules) {
        List<QueryRenderRule> safe = CollUtil.isEmpty(rules) ? List.of() : rules;
        for (QueryRenderRule r : safe) {
            Assert.isFalse(StrUtil.isBlank(r.getFieldKey()), "rule.fieldKey blank");
            Assert.notNull(r.getOp(), "rule.op is null");
            Assert.isFalse(StrUtil.isBlank(r.getTemplate()), "rule.template blank");
        }
        Function<QueryRenderRule, String> keyFn = r -> StrUtil.join("|",
                r.getFieldKey(),
                r.getOp().getCode(),
                Optional.ofNullable(r.getMatchType()).map(Enum::name).orElse("*"),
                Optional.ofNullable(r.getNegated()).map(String::valueOf).orElse("*"),
                Optional.ofNullable(r.getValueType()).map(Enum::name).orElse("*")
        );
        Map<String, Long> dup = safe.stream()
                .collect(Collectors.groupingBy(keyFn, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Assert.isTrue(dup.isEmpty(), "duplicate rules: {}", dup.keySet());
    }

    private void registerUpdated() {
        this.registerEvent(new LiteratureProvenanceEvents.LiteratureProvenanceUpdated(
                this.id, this.code.getCode(), this.name, this.version, LocalDateTime.now()
        ));
    }

    private static <T> List<T> toSafeMutableList(List<T> list) {
        return CollUtil.isEmpty(list) ? new ArrayList<>() : new ArrayList<>(list);
    }
}
