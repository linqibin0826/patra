package com.patra.starter.expr.compiler.snapshot;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 调用方规则快照（不可变）。
 * 由 Loader（Feign/File/Inline）装载，作为编译器域层唯一事实输入。
 */
@Builder
public record ProvenanceSnapshot(
        ProvenanceKey key,
        String operation,
        long version,
        Instant updatedAt,
        Map<String, FieldDictEntry> fieldDict,         // fieldKey -> 定义
        Map<String, CapabilityRule> capability,        // fieldKey -> 能力规则
        List<RenderRuleTemplate> renderRules,          // 已按 priority 降序
        Map<String, ApiParamMappingEntry> apiParam     // stdKey -> 映射
) {
    public record ProvenanceKey(Long id, String code) {}

    public record FieldDictEntry(
            String fieldKey,
            DataType dataType,         // date/datetime/number/text/keyword/boolean/token
            Cardinality cardinality,   // single/multi
            boolean isDateField,       // 注意：避免使用关键字
            String datetype            // PDAT/EDAT/MHDA
    ) {
        public enum DataType { date, datetime, number, text, keyword, bool, token }
        public enum Cardinality { single, multi }
    }

    public record CapabilityRule(
            List<String> ops,                      // ["TERM","IN","RANGE","EXISTS","TOKEN"]
            List<String> negatableOps,             // 允许 NOT 的子集；null 表示同 ops
            boolean supportsNotOp,
            List<String> termMatches,              // ["PHRASE","EXACT","ANY"]
            boolean termCaseSensitiveAllowed,
            boolean termAllowBlank,
            int termMinLen,
            int termMaxLen,
            String termPattern,
            int inMaxSize,
            boolean inCaseSensitiveAllowed,
            RangeKind rangeKind,                   // NONE/DATE/DATETIME/NUMBER
            boolean rangeAllowOpenStart,
            boolean rangeAllowOpenEnd,
            boolean rangeAllowClosedAtInfty,
            LocalDate dateMin,                        // yyyy-MM-dd
            LocalDate dateMax,                        // yyyy-MM-dd
            Instant datetimeMin,                   // UTC
            Instant datetimeMax,                   // UTC
            String numberMin,                      // 用字符串承载高精度
            String numberMax,
            boolean existsSupported,
            List<String> tokenKinds,               // 小写
            String tokenValuePattern
    ) { public enum RangeKind { NONE, DATE, DATETIME, NUMBER } }

    public record RenderRuleTemplate(
            String fieldKey,
            Op op,                      // term/in/range/exists/token
            String matchType,           // phrase/exact/any（TERM）
            Boolean negated,            // 可空=不区分
            String valueType,           // string/date/datetime/number（RANGE）
            Emit emit,                  // query/params
            int priority,               // 越大越优先
            String template,            // query 模板
            String itemTemplate,        // IN 单项模板
            String joiner,              // 集合连接符
            boolean wrapGroup,          // 集合是否包裹
            Map<String, String> params, // 标准键 -> 供应商参数名（当前规则）
            String fn                   // 自定义渲染函数名
    ) {
        public enum Emit { query, params }
        public enum Op { term, in, range, exists, token }
    }

    public record ApiParamMappingEntry(
            String stdKey,
            String providerParam,
            String transform
    ) {}
}
