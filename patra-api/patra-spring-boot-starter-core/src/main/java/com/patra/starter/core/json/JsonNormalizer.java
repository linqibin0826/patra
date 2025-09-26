package com.patra.starter.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JSON 规范化工具，用于生成确定性的结构和值。
 */
public final class JsonNormalizer {

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final DateTimeFormatter CANONICAL_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final List<DateTimeFormatter> SUPPORTED_TEMPORAL_FORMATTERS = List.of(
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd['T'][' ']HH:mm:ss[.SSS][XXX][XX][X]").toFormatter(),
            new DateTimeFormatterBuilder().appendPattern("yyyy/MM/dd['T'][' ']HH:mm:ss[.SSS][XXX][XX][X]").toFormatter(),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withLocale(Locale.ROOT)
    );

    private static final List<DateTimeFormatter> SUPPORTED_DATE_ONLY_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withLocale(Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyyMMdd").withLocale(Locale.ROOT)
    );

    private final ObjectMapper objectMapper;
    private final Config config;
    private final ObjectWriter canonicalWriter;

    private JsonNormalizer(ObjectMapper objectMapper, Config config) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.config = Objects.requireNonNull(config, "config");
        this.canonicalWriter = objectMapper.writer()
                .without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                .withFeatures(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    /**
     * 使用默认配置和全局 ObjectMapper 进行规范化。
     */
    public static Result normalizeDefault(Object input) {
        return usingDefault().normalize(input);
    }

    /**
     * 创建使用默认配置的 JsonNormalizer。
     */
    public static JsonNormalizer usingDefault() {
        return new JsonNormalizer(JacksonProvider.getObjectMapper(), Config.builder().build());
    }

    /**
     * 使用特定配置的 JsonNormalizer。
     */
    public static JsonNormalizer withConfig(Config config) {
        return new JsonNormalizer(JacksonProvider.getObjectMapper(), config);
    }

    /**
     * 使用提供的 ObjectMapper 与配置创建实例。
     */
    public static JsonNormalizer withMapper(ObjectMapper objectMapper, Config config) {
        return new JsonNormalizer(objectMapper, config);
    }

    /**
     * 对输入进行规范化处理。
     */
    public Result normalize(Object input) {
        JsonNode root = toJsonNode(input);
        NormalizationPath path = new NormalizationPath();
        NormalizedValue normalized = normalizeNode(root, path, 1);
        Object canonical = normalized.present() ? normalized.value() : null;
        String canonicalJson = writeCanonicalJson(canonical);
        byte[] hashMaterial = canonicalJson.getBytes(StandardCharsets.UTF_8);
        return new Result(canonical, canonicalJson, hashMaterial);
    }

    private JsonNode toJsonNode(Object input) {
        if (input == null) {
            return NullNode.getInstance();
        }
        try {
            if (input instanceof JsonNode jsonNode) {
                return jsonNode;
            }
            if (input instanceof CharSequence seq) {
                String text = seq.toString();
                return text.isEmpty() ? NullNode.getInstance() : objectMapper.readTree(text);
            }
            return objectMapper.valueToTree(input);
        } catch (JsonProcessingException ex) {
            throw new JsonNormalizationException("Unable to parse JSON input", ex);
        }
    }

    private NormalizedValue normalizeNode(JsonNode node, NormalizationPath path, int depth) {
        if (depth > config.maxDepth) {
            throw new JsonNormalizationException("Max depth exceeded at path " + path.asString(true));
        }
        if (node == null || node.isMissingNode()) {
            return NormalizedValue.absent();
        }
        if (node.isNull()) {
            return applyEmptyPolicy(null, path);
        }
        return switch (node.getNodeType()) {
            case OBJECT -> normalizeObject((ObjectNode) node, path, depth);
            case ARRAY -> normalizeArray((ArrayNode) node, path, depth);
            case STRING -> normalizeText(node.textValue(), path);
            case NUMBER -> normalizeNumber(node, path);
            case BOOLEAN -> NormalizedValue.of(Boolean.valueOf(node.booleanValue()));
            case BINARY -> normalizeBinary(node, path);
            case POJO -> normalizePojo((POJONode) node, path, depth);
            default -> throw new JsonNormalizationException("Unsupported JSON node: " + node.getNodeType());
        };
    }

    private NormalizedValue normalizePojo(POJONode node, NormalizationPath path, int depth) {
        JsonNode converted = objectMapper.valueToTree(node.getPojo());
        return normalizeNode(converted, path, depth + 1);
    }

    private NormalizedValue normalizeBinary(JsonNode node, NormalizationPath path) {
        try {
            byte[] data = node.binaryValue();
            if (data == null || data.length == 0) {
                return applyEmptyPolicy(null, path);
            }
            String encoded = Base64.getEncoder().encodeToString(data);
            ensureStringWithinLimit(encoded, path);
            return applyEmptyPolicy(encoded, path);
        } catch (IOException ex) {
            throw new JsonNormalizationException("Failed to read binary data", ex);
        }
    }

    private NormalizedValue normalizeObject(ObjectNode objectNode, NormalizationPath path, int depth) {
        List<String> keys = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(keys::add);
        keys.sort(config.keyComparator);

        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            if (config.forbidKeys.contains(key)) {
                throw new JsonNormalizationException("Forbidden key encountered: " + key);
            }
            JsonNode child = objectNode.get(key);
            path.pushField(key);
            NormalizedValue normalized = normalizeNode(child, path, depth + 1);
            path.pop();
            if (normalized.present()) {
                result.put(key, normalized.value());
            }
        }
        Map<String, Object> immutable = Collections.unmodifiableMap(result);
        return applyEmptyPolicy(immutable, path);
    }

    private NormalizedValue normalizeArray(ArrayNode arrayNode, NormalizationPath path, int depth) {
        path.markArray();
        NormalizedValue outcome;
        try {
            boolean isSequence = matchesFieldOrPath(config.sequenceFieldWhitelist, path);
            List<ArrayElement> elements = new ArrayList<>();
            for (JsonNode elementNode : arrayNode) {
                NormalizedValue normalized = normalizeNode(elementNode, path, depth + 1);
                if (!normalized.present()) {
                    continue;
                }
                Object value = normalized.value();
                String typeTag = typeTag(value);
                String serialized = writeCanonicalJson(value);
                elements.add(new ArrayElement(value, typeTag, serialized));
            }
            List<Object> values;
            if (isSequence) {
                values = elements.stream().map(ArrayElement::value).toList();
            } else {
                List<ArrayElement> working = new ArrayList<>();
                if (config.arrayDeduplicate) {
                    Map<String, ArrayElement> dedup = new LinkedHashMap<>();
                    for (ArrayElement element : elements) {
                        String identity = element.typeTag + '|' + element.serialized;
                        dedup.putIfAbsent(identity, element);
                    }
                    working.addAll(dedup.values());
                } else {
                    working.addAll(elements);
                }
                working.sort(Comparator
                        .comparing(ArrayElement::typeTag)
                        .thenComparing(ArrayElement::serialized));
                values = working.stream().map(ArrayElement::value).toList();
            }
            List<Object> immutable = List.copyOf(values);
            outcome = applyEmptyPolicy(immutable, path);
        } finally {
            path.unmarkArray();
        }
        return outcome;
    }

    private NormalizedValue normalizeText(String text, NormalizationPath path) {
        if (text == null) {
            return applyEmptyPolicy(null, path);
        }
        String normalized = text;
        if (config.trimStrings) {
            normalized = normalized.trim();
        }
        if (config.collapseSpaces) {
            normalized = SPACE_PATTERN.matcher(normalized).replaceAll(" ");
        }
        if (matchesFieldOrPath(config.lowercaseFields, path)) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        ensureStringWithinLimit(normalized, path);
        if (config.coerceBoolean != Config.CoerceBoolean.NONE) {
            Optional<Boolean> bool = coerceBoolean(normalized, config.coerceBoolean);
            if (bool.isPresent()) {
                return NormalizedValue.of(bool.get());
            }
        }
        if (config.coerceTime) {
            Optional<String> temporal = coerceTemporal(normalized);
            if (temporal.isPresent()) {
                return applyEmptyPolicy(temporal.get(), path);
            }
        }
        if (config.coerceNumber) {
            Optional<BigDecimal> numeric = coerceNumeric(normalized);
            if (numeric.isPresent()) {
                return NormalizedValue.of(sanitizeBigDecimal(numeric.get()));
            }
        }
        return applyEmptyPolicy(normalized, path);
    }

    private NormalizedValue normalizeNumber(JsonNode node, NormalizationPath path) {
        if (node.isFloatingPointNumber()) {
            double value = node.doubleValue();
            if (!Double.isFinite(value)) {
                throw new JsonNormalizationException("Non-finite number encountered at " + path.asString(true));
            }
        }
        BigDecimal decimal = node.decimalValue();
        if (config.coerceBoolean == Config.CoerceBoolean.LOOSE && isZeroOrOne(decimal)) {
            return NormalizedValue.of(decimal.compareTo(BigDecimal.ONE) == 0);
        }
        if (config.coerceTime) {
            Optional<String> temporal = coerceTemporal(decimal);
            if (temporal.isPresent()) {
                return NormalizedValue.of(temporal.get());
            }
        }
        BigDecimal sanitized = config.coerceNumber ? sanitizeBigDecimal(decimal) : decimal;
        return NormalizedValue.of(sanitized);
    }

    private Optional<Boolean> coerceBoolean(String text, Config.CoerceBoolean mode) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (mode == Config.CoerceBoolean.STRICT) {
            return switch (lower) {
                case "true" -> Optional.of(Boolean.TRUE);
                case "false" -> Optional.of(Boolean.FALSE);
                default -> Optional.empty();
            };
        }
        return switch (lower) {
            case "true", "1", "yes" -> Optional.of(Boolean.TRUE);
            case "false", "0", "no" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    private Optional<String> coerceTemporal(String value) {
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (value.matches("^-?\\d+$")) {
            try {
                long epoch = Long.parseLong(value);
                Instant instant = epochToInstant(epoch);
                return Optional.of(formatInstant(instant));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        for (DateTimeFormatter formatter : SUPPORTED_TEMPORAL_FORMATTERS) {
            try {
                TemporalAccessorWrapper accessor = new TemporalAccessorWrapper(formatter.parse(value));
                Instant instant = accessor.toInstant(config.defaultZoneId);
                return Optional.of(formatInstant(instant));
            } catch (DateTimeParseException ignored) {
                // continue trying other formats
            }
        }
        for (DateTimeFormatter formatter : SUPPORTED_DATE_ONLY_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(value, formatter);
                Instant instant = date.atStartOfDay(config.defaultZoneId).toInstant();
                return Optional.of(formatInstant(instant));
            } catch (DateTimeParseException ignored) {
                // continue
            }
        }
        return Optional.empty();
    }

    private Optional<String> coerceTemporal(BigDecimal decimal) {
        if (decimal.scale() > 0) {
            return Optional.empty();
        }
        try {
            long epoch = decimal.longValueExact();
            Instant instant = epochToInstant(epoch);
            return Optional.of(formatInstant(instant));
        } catch (ArithmeticException ex) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> coerceNumeric(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.equalsIgnoreCase("nan") || value.equalsIgnoreCase("infinity") || value.equalsIgnoreCase("-infinity")) {
            throw new JsonNormalizationException("Illegal numeric literal: " + value);
        }
        try {
            return Optional.of(new BigDecimal(value, MathContext.UNLIMITED));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private boolean isZeroOrOne(BigDecimal decimal) {
        return decimal.compareTo(BigDecimal.ZERO) == 0 || decimal.compareTo(BigDecimal.ONE) == 0;
    }

    private BigDecimal sanitizeBigDecimal(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped;
    }

    private Instant epochToInstant(long epoch) {
        if (epoch > 3_000_000_000_000L || epoch < -3_000_000_000_000L) {
            // safety guard against absurd values
            throw new JsonNormalizationException("Epoch value out of range: " + epoch);
        }
        if (Math.abs(epoch) >= 1_000_000_000_000L) {
            return Instant.ofEpochMilli(epoch);
        }
        return Instant.ofEpochSecond(epoch);
    }

    private String formatInstant(Instant instant) {
        Instant truncated = instant.truncatedTo(ChronoUnit.MILLIS);
        return CANONICAL_INSTANT.format(truncated);
    }

    private void ensureStringWithinLimit(String value, NormalizationPath path) {
        if (config.maxStringBytes <= 0) {
            return;
        }
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > config.maxStringBytes) {
            throw new JsonNormalizationException(
                    "String length exceeds limit at " + path.asString(true) + ": " + length + " > " + config.maxStringBytes);
        }
    }

    private NormalizedValue applyEmptyPolicy(Object value, NormalizationPath path) {
        boolean empty = isEmptyValue(value);
        if (empty && config.removeEmpty && !matchesFieldOrPath(config.keepEmptyWhitelist, path)) {
            return NormalizedValue.absent();
        }
        return NormalizedValue.of(value);
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence sequence) {
            return sequence.length() == 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private boolean matchesFieldOrPath(Set<String> configured, NormalizationPath path) {
        if (configured.isEmpty()) {
            return false;
        }
        String noRoot = path.asString(false);
        if (!noRoot.isEmpty() && configured.contains(noRoot)) {
            return true;
        }
        String withRoot = path.asString(true);
        if (configured.contains(withRoot)) {
            return true;
        }
        String leaf = path.leaf();
        return !leaf.isEmpty() && configured.contains(leaf);
    }

    private String writeCanonicalJson(Object value) {
        try {
            return canonicalWriter.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonNormalizationException("Failed to serialize canonical JSON", ex);
        }
    }

    private String typeTag(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Boolean) {
            return "1";
        }
        if (value instanceof BigDecimal) {
            return "2";
        }
        if (value instanceof CharSequence) {
            return "3";
        }
        if (value instanceof Map) {
            return "4";
        }
        if (value instanceof List) {
            return "5";
        }
        return "9";
    }

    /**
     * 规范化结果。
     */
    public static final class Result {
        private final Object canonicalValue;
        private final String canonicalJson;
        private final byte[] hashMaterial;

        Result(Object canonicalValue, String canonicalJson, byte[] hashMaterial) {
            this.canonicalValue = canonicalValue;
            this.canonicalJson = canonicalJson;
            this.hashMaterial = hashMaterial;
        }

        public Object getCanonicalValue() {
            return canonicalValue;
        }

        public String getCanonicalJson() {
            return canonicalJson;
        }

        public byte[] getHashMaterial() {
            return hashMaterial;
        }
    }

    /**
     * 配置项。
     */
    public static final class Config {
        private final boolean removeEmpty;
        private final Set<String> keepEmptyWhitelist;
        private final CoerceBoolean coerceBoolean;
        private final boolean coerceNumber;
        private final boolean coerceTime;
        private final ZoneId defaultZoneId;
        private final Set<String> sequenceFieldWhitelist;
        private final boolean arrayDeduplicate;
        private final boolean trimStrings;
        private final boolean collapseSpaces;
        private final Set<String> lowercaseFields;
        private final Comparator<String> keyComparator;
        private final int maxDepth;
        private final int maxStringBytes;
        private final Set<String> forbidKeys;

        private Config(Builder builder) {
            this.removeEmpty = builder.removeEmpty;
            this.keepEmptyWhitelist = Collections.unmodifiableSet(new LinkedHashSet<>(builder.keepEmptyWhitelist));
            this.coerceBoolean = builder.coerceBoolean;
            this.coerceNumber = builder.coerceNumber;
            this.coerceTime = builder.coerceTime;
            this.defaultZoneId = builder.defaultZoneId;
            this.sequenceFieldWhitelist = Collections.unmodifiableSet(new LinkedHashSet<>(builder.sequenceFieldWhitelist));
            this.arrayDeduplicate = builder.arrayDeduplicate;
            this.trimStrings = builder.trimStrings;
            this.collapseSpaces = builder.collapseSpaces;
            this.lowercaseFields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.lowercaseFields));
            this.keyComparator = builder.sortComparator.comparator;
            this.maxDepth = builder.maxDepth;
            this.maxStringBytes = builder.maxStringBytes;
            this.forbidKeys = Collections.unmodifiableSet(new HashSet<>(builder.forbidKeys));
        }

        public static Builder builder() {
            return new Builder();
        }

        public enum CoerceBoolean {
            NONE,
            STRICT,
            LOOSE
        }

        public enum SortComparator {
            ASCII(Comparator.naturalOrder()),
            UNICODE(java.text.Collator.getInstance(Locale.ROOT)::compare);

            private final Comparator<String> comparator;

            SortComparator(Comparator<String> comparator) {
                this.comparator = comparator;
            }
        }

        public static final class Builder {
            private boolean removeEmpty = true;
            private final Set<String> keepEmptyWhitelist = new LinkedHashSet<>();
            private CoerceBoolean coerceBoolean = CoerceBoolean.LOOSE;
            private boolean coerceNumber = true;
            private boolean coerceTime = true;
            private ZoneId defaultZoneId = ZoneOffset.UTC;
            private final Set<String> sequenceFieldWhitelist = new LinkedHashSet<>();
            private boolean arrayDeduplicate = true;
            private boolean trimStrings = true;
            private boolean collapseSpaces = true;
            private final Set<String> lowercaseFields = new LinkedHashSet<>();
            private SortComparator sortComparator = SortComparator.ASCII;
            private int maxDepth = 64;
            private int maxStringBytes = 64 * 1024;
            private final Set<String> forbidKeys = new HashSet<>();

            public Builder removeEmpty(boolean removeEmpty) {
                this.removeEmpty = removeEmpty;
                return this;
            }

            public Builder keepEmptyWhitelist(Set<String> fields) {
                this.keepEmptyWhitelist.clear();
                if (fields != null) {
                    this.keepEmptyWhitelist.addAll(fields);
                }
                return this;
            }

            public Builder coerceBoolean(CoerceBoolean coerceBoolean) {
                this.coerceBoolean = Objects.requireNonNull(coerceBoolean, "coerceBoolean");
                return this;
            }

            public Builder coerceNumber(boolean coerceNumber) {
                this.coerceNumber = coerceNumber;
                return this;
            }

            public Builder coerceTime(boolean coerceTime) {
                this.coerceTime = coerceTime;
                return this;
            }

            public Builder defaultZoneId(ZoneId defaultZoneId) {
                this.defaultZoneId = Objects.requireNonNull(defaultZoneId, "defaultZoneId");
                return this;
            }

            public Builder sequenceFieldWhitelist(Set<String> fields) {
                this.sequenceFieldWhitelist.clear();
                if (fields != null) {
                    this.sequenceFieldWhitelist.addAll(fields);
                }
                return this;
            }

            public Builder arrayDeduplicate(boolean arrayDeduplicate) {
                this.arrayDeduplicate = arrayDeduplicate;
                return this;
            }

            public Builder trimStrings(boolean trimStrings) {
                this.trimStrings = trimStrings;
                return this;
            }

            public Builder collapseSpaces(boolean collapseSpaces) {
                this.collapseSpaces = collapseSpaces;
                return this;
            }

            public Builder lowercaseFields(Set<String> fields) {
                this.lowercaseFields.clear();
                if (fields != null) {
                    this.lowercaseFields.addAll(fields);
                }
                return this;
            }

            public Builder sortComparator(SortComparator sortComparator) {
                this.sortComparator = Objects.requireNonNull(sortComparator, "sortComparator");
                return this;
            }

            public Builder maxDepth(int maxDepth) {
                if (maxDepth <= 0) {
                    throw new IllegalArgumentException("maxDepth must be > 0");
                }
                this.maxDepth = maxDepth;
                return this;
            }

            public Builder maxStringBytes(int maxStringBytes) {
                if (maxStringBytes < 0) {
                    throw new IllegalArgumentException("maxStringBytes must be >= 0");
                }
                this.maxStringBytes = maxStringBytes;
                return this;
            }

            public Builder forbidKeys(Set<String> keys) {
                this.forbidKeys.clear();
                if (keys != null) {
                    this.forbidKeys.addAll(keys);
                }
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }

    private record ArrayElement(Object value, String typeTag, String serialized) {
    }

    private record NormalizedValue(boolean present, Object value) {
        static NormalizedValue absent() {
            return new NormalizedValue(false, null);
        }

        static NormalizedValue of(Object value) {
            return new NormalizedValue(true, value);
        }
    }

    private static final class NormalizationPath {
        private final Deque<String> tokens = new ArrayDeque<>();

        void pushField(String field) {
            tokens.addLast(field);
        }

        void pop() {
            if (!tokens.isEmpty()) {
                tokens.removeLast();
            }
        }

        void markArray() {
            if (tokens.isEmpty()) {
                tokens.addLast("[]");
            } else {
                String last = tokens.removeLast();
                tokens.addLast(last + "[]");
            }
        }

        void unmarkArray() {
            if (tokens.isEmpty()) {
                return;
            }
            String last = tokens.removeLast();
            if (last.endsWith("[]")) {
                String base = last.substring(0, last.length() - 2);
                if (!base.isEmpty()) {
                    tokens.addLast(base);
                }
            } else {
                tokens.addLast(last);
            }
        }

        String asString(boolean includeRoot) {
            if (tokens.isEmpty()) {
                return includeRoot ? "$" : "";
            }
            String joined = String.join(".", tokens);
            return includeRoot ? "$." + joined : joined;
        }

        String leaf() {
            if (tokens.isEmpty()) {
                return "";
            }
            String last = tokens.peekLast();
            while (last != null && last.endsWith("[]")) {
                last = last.substring(0, last.length() - 2);
            }
            return last == null ? "" : last;
        }
    }

    /**
     * 规范化异常。
     */
    public static class JsonNormalizationException extends RuntimeException {
        public JsonNormalizationException(String message) {
            super(message);
        }

        public JsonNormalizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class TemporalAccessorWrapper {
        private final TemporalAccessor accessor;

        TemporalAccessorWrapper(TemporalAccessor accessor) {
            this.accessor = accessor;
        }

        Instant toInstant(ZoneId defaultZone) {
            if (accessor.isSupported(ChronoField.INSTANT_SECONDS)) {
                return Instant.from(accessor);
            }
            if (accessor.isSupported(ChronoField.OFFSET_SECONDS)) {
                return OffsetDateTime.from(accessor).toInstant();
            }
            if (accessor.isSupported(ChronoField.HOUR_OF_DAY)) {
                LocalDateTime dateTime = LocalDateTime.from(accessor);
                return dateTime.atZone(defaultZone).toInstant();
            }
            if (accessor.isSupported(ChronoField.DAY_OF_MONTH)) {
                LocalDate date = LocalDate.from(accessor);
                return date.atStartOfDay(defaultZone).toInstant();
            }
            throw new JsonNormalizationException("Unsupported temporal accessor: " + accessor);
        }
    }
}
