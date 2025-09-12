package com.patra.starter.expr.compiler.snapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 规则快照聚合：域层只认这个不可变快照。
 * 来自 registry / 文件 / 内联等多种 Loader 的同构产物。
 */
public record ProvenanceSnapshot(
        String provenanceCode,                 // 业务键，如 "pubmed"
        String provenanceName,                 // 人类可读名
        String operation,                      // search/fetch/lookup…
        FieldDict fieldDict,                   // 字段字典
        CapabilityMatrix capabilityMatrix,     // 能力矩阵
        RenderRuleSet renderRuleSet,           // 渲染规则集
        ApiParamMappings apiParamMappings,     // API 参数映射
        Meta meta                               // 版本与审计元信息
) {

    public record FieldDict(
            // 按内部字段键（如 ti/ab/tiab/dp…）索引
            Map<String, Field> fields
    ) {
        public record Field(
                String key,                    // 字段键（小写蛇形/内部缩写）
                DataType dataType,             // date/datetime/number/text/keyword/boolean/token
                Cardinality cardinality,       // single/multi
                boolean isDate,                // 是否日期字段
                String datetype                // 仅日期类：PDAT/EDAT/MHDA，可为 null
        ) {
        }

        public enum DataType {date, datetime, number, text, keyword, boolean_, token}

        public enum Cardinality {single, multi}
    }

    public record CapabilityMatrix(
            // 每个字段一条能力记录
            Map<String, FieldCapability> byField
    ) {
        public record FieldCapability(
                List<Op> ops,                  // 允许的操作：TERM/IN/RANGE/EXISTS/TOKEN
                List<Op> negatableOps,         // 可取反操作（null=同 ops）
                boolean supportsNot,           // 字段层面的 NOT 总开关
                List<TermMatch> termMatches,   // TERM: PHRASE/EXACT/ANY
                boolean termCaseSensitiveAllowed,
                boolean termAllowBlank,
                int termMinLen,
                int termMaxLen,
                String termPattern,            // 正则
                int inMaxSize,
                boolean inCaseSensitiveAllowed,
                RangeKind rangeKind,           // NONE/DATE/DATETIME/NUMBER
                boolean rangeAllowOpenStart,
                boolean rangeAllowOpenEnd,
                boolean rangeAllowClosedAtInfty,
                String dateMin,                // yyyy-MM-dd，nullable
                String dateMax,
                String datetimeMin,            // ISO-8601 UTC，nullable
                String datetimeMax,
                String numberMin,              // decimal in string，避免精度丢失
                String numberMax,
                boolean existsSupported,
                List<String> tokenKinds,       // ["owner","pmcid"]
                String tokenValuePattern
        ) {
        }

        public enum Op {TERM, IN, RANGE, EXISTS, TOKEN}

        public enum TermMatch {PHRASE, EXACT, ANY}

        public enum RangeKind {NONE, DATE, DATETIME, NUMBER}
    }

    public record RenderRuleSet(
            // 以字段键索引 → 规则列表（内部按 priority 降序）
            Map<String, List<RenderRule>> byField
    ) {
        public record RenderRule(
                String fieldKey,
                Op op,
                MatchType matchType,           // 仅 TERM 有意义，可为 null
                Boolean negated,               // 是否针对取反情形；null=不区分
                ValueType valueType,           // RANGE 值类型；其他 op 可为 null
                Emit emit,                     // query | params
                int priority,
                String template,               // query 模板
                String itemTemplate,           // IN 集合展开单项模板，可空
                String joiner,                 // 集合连接符，如 " OR "
                boolean wrapGroup,
                Map<String, String> params,    // 标准键→供应商参数名
                String fn                      // 可选渲染函数名
        ) {
            public enum Op {term, in, range, exists, token}

            public enum MatchType {phrase, exact, any}

            public enum ValueType {string, date, datetime, number}

            public enum Emit {query, params}
        }
    }

    public record ApiParamMappings(
            // 按 operation 聚合：每个 stdKey → providerParam 与 transform
            Map<String, Map<String, Mapping>> byOperation
    ) {
        public record Mapping(
                String stdKey,
                String providerParam,
                String transform // 如 toExclusiveMinus1d，可空
        ) {
        }
    }

    public record Meta(
            String versionTag,                // 版本签：可拼装自 registry 的 version/updatedAt
            Instant snapshotAtUTC,            // 快照生成时间
            List<String> recordRemarks        // 审计/注释
    ) {
    }
}
