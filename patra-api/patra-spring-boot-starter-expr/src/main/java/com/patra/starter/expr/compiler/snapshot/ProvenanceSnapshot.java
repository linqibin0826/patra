package com.patra.starter.expr.compiler.snapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ProvenanceSnapshot(
        Identity identity,
        Scope scope,
        Operation operation,
        long version,
        Instant capturedAt,
        Map<String, FieldDefinition> fieldDictionary,
        Map<String, Capability> capabilityMatrix,
        Map<String, ApiParameter> apiParameterMap,
        List<RenderRule> renderRules
) {

    public ProvenanceSnapshot {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(fieldDictionary, "fieldDictionary");
        Objects.requireNonNull(capabilityMatrix, "capabilityMatrix");
        Objects.requireNonNull(apiParameterMap, "apiParameterMap");
        Objects.requireNonNull(renderRules, "renderRules");
        fieldDictionary = Map.copyOf(fieldDictionary);
        capabilityMatrix = Map.copyOf(capabilityMatrix);
        apiParameterMap = Map.copyOf(apiParameterMap);
        renderRules = List.copyOf(renderRules);
    }

    public record Identity(Long provenanceId, String code, String name) {
        public Identity {
            Objects.requireNonNull(code, "code");
        }
    }

    public record Scope(String scopeCode, String taskTypeKey) {
        public static Scope sourceScope() {
            return new Scope("SOURCE", null);
        }
    }

    public record Operation(String code, String defaultTimezone) {
        public Operation {
            Objects.requireNonNull(code, "code");
        }
    }

    public record FieldDefinition(
            String fieldKey,
            String displayName,
            String description,
            DataType dataType,
            Cardinality cardinality,
            boolean exposable,
            boolean dateField
    ) {
        public FieldDefinition {
            Objects.requireNonNull(fieldKey, "fieldKey");
            Objects.requireNonNull(dataType, "dataType");
            Objects.requireNonNull(cardinality, "cardinality");
        }
    }

    public enum DataType {
        DATE,
        DATETIME,
        NUMBER,
        TEXT,
        KEYWORD,
        BOOLEAN,
        TOKEN
    }

    public enum Cardinality {
        SINGLE,
        MULTI
    }

    public record Capability(
            Set<String> ops,
            Set<String> negatableOps,
            boolean supportsNot,
            Set<String> termMatches,
            boolean termCaseSensitiveAllowed,
            boolean termAllowBlank,
            int termMinLength,
            int termMaxLength,
            String termPattern,
            int inMaxSize,
            boolean inCaseSensitiveAllowed,
            RangeKind rangeKind,
            boolean rangeAllowOpenStart,
            boolean rangeAllowOpenEnd,
            boolean rangeAllowClosedAtInfinity,
            LocalDate dateMin,
            LocalDate dateMax,
            Instant datetimeMin,
            Instant datetimeMax,
            String numberMin,
            String numberMax,
            boolean existsSupported,
            Set<String> tokenKinds,
            String tokenValuePattern
    ) {
        public Capability {
            Objects.requireNonNull(ops, "ops");
            Objects.requireNonNull(rangeKind, "rangeKind");
            ops = Set.copyOf(ops);
            negatableOps = negatableOps == null ? Set.of() : Set.copyOf(negatableOps);
            termMatches = termMatches == null ? Set.of() : Set.copyOf(termMatches);
            tokenKinds = tokenKinds == null ? Set.of() : Set.copyOf(tokenKinds);
        }
    }

    public enum RangeKind {
        NONE,
        DATE,
        DATETIME,
        NUMBER
    }

    public record ApiParameter(
            String stdKey,
            String providerParamName,
            String transformCode,
            String notesJson
    ) {
        public ApiParameter {
            Objects.requireNonNull(stdKey, "stdKey");
        }
    }

    public record RenderRule(
            String fieldKey,
            String scopeCode,
            String taskTypeKey,
            com.patra.expr.Atom.Operator operator,
            String matchTypeCode,
            NegationQualifier negation,
            ValueType valueType,
            EmitType emitType,
            String template,
            String itemTemplate,
            String joiner,
            boolean wrapGroup,
            Map<String, String> params,
            String functionCode,
            Instant effectiveFrom,
            Instant effectiveTo,
            int priority
    ) {
        public RenderRule {
            Objects.requireNonNull(fieldKey, "fieldKey");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(emitType, "emitType");
            if (params != null) {
                params = Map.copyOf(params);
            }
        }
    }

    public enum NegationQualifier {
        ANY,
        TRUE,
        FALSE
    }

    public enum ValueType {
        ANY,
        STRING,
        DATE,
        DATETIME,
        NUMBER
    }

    public enum EmitType {
        QUERY,
        PARAMS
    }
}
