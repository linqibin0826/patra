package com.patra.common.json;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration governing JSON normalization behavior.
 *
 * <p>Provides tunable options including empty-value removal, type coercion strategies, default time
 * zone, sequence-field preservation, array deduplication, string cleanup, key sorting, and safety
 * guards (depth, string byte length, forbidden keys).
 *
 * <p>Use {@link #builder()} to construct instances with custom settings.
 */
public final class JsonNormalizerConfig {
  final boolean removeEmpty;
  final Set<String> keepEmptyWhitelist;
  final CoerceBoolean coerceBoolean;
  final boolean coerceNumber;
  final boolean coerceTime;
  final ZoneId defaultZoneId;
  final Set<String> sequenceFieldWhitelist;
  final boolean arrayDeduplicate;
  final boolean trimStrings;
  final boolean collapseSpaces;
  final Set<String> lowercaseFields;
  final Comparator<String> keyComparator;
  final int maxDepth;
  final int maxStringBytes;
  final Set<String> forbidKeys;

  private JsonNormalizerConfig(Builder builder) {
    this.removeEmpty = builder.removeEmpty;
    this.keepEmptyWhitelist =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.keepEmptyWhitelist));
    this.coerceBoolean = builder.coerceBoolean;
    this.coerceNumber = builder.coerceNumber;
    this.coerceTime = builder.coerceTime;
    this.defaultZoneId = builder.defaultZoneId;
    this.sequenceFieldWhitelist =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.sequenceFieldWhitelist));
    this.arrayDeduplicate = builder.arrayDeduplicate;
    this.trimStrings = builder.trimStrings;
    this.collapseSpaces = builder.collapseSpaces;
    this.lowercaseFields =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.lowercaseFields));
    this.keyComparator = builder.sortComparator.comparator;
    this.maxDepth = builder.maxDepth;
    this.maxStringBytes = builder.maxStringBytes;
    this.forbidKeys = Collections.unmodifiableSet(new HashSet<>(builder.forbidKeys));
  }

  /** Creates a new builder for constructing configuration instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Boolean coercion strategy for string-to-boolean conversion. */
  public enum CoerceBoolean {
    /** No coercion; booleans remain as parsed. */
    NONE,

    /** Strict coercion: only "true" and "false" strings. */
    STRICT,

    /** Loose coercion: "true"/"1"/"yes" to true, "false"/"0"/"no" to false. */
    LOOSE
  }

  /** Comparator strategy for sorting JSON object keys. */
  public enum SortComparator {
    /** ASCII natural order (byte-by-byte comparison). */
    ASCII(Comparator.naturalOrder()),

    /** Unicode collation order using default locale. */
    UNICODE(java.text.Collator.getInstance(Locale.ROOT)::compare);

    private final Comparator<String> comparator;

    SortComparator(Comparator<String> comparator) {
      this.comparator = comparator;
    }
  }

  /**
   * Builder for {@link JsonNormalizerConfig}.
   *
   * <p>Defaults favor stable yet lenient normalization with noise reduction; override options as
   * needed for stricter or more permissive behavior.
   */
  public static final class Builder {
    private boolean removeEmpty = true;
    private final Set<String> keepEmptyWhitelist = new LinkedHashSet<>();
    private CoerceBoolean coerceBoolean = CoerceBoolean.NONE;
    private boolean coerceNumber = true;
    private boolean coerceTime = false;
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

    /** Sets whether to remove empty values (null, empty strings, empty collections/maps). */
    public Builder removeEmpty(boolean removeEmpty) {
      this.removeEmpty = removeEmpty;
      return this;
    }

    /** Sets fields that should preserve empty values even when removeEmpty is true. */
    public Builder keepEmptyWhitelist(Set<String> fields) {
      this.keepEmptyWhitelist.clear();
      if (fields != null) {
        this.keepEmptyWhitelist.addAll(fields);
      }
      return this;
    }

    /** Sets the boolean coercion strategy. */
    public Builder coerceBoolean(CoerceBoolean coerceBoolean) {
      this.coerceBoolean = Objects.requireNonNull(coerceBoolean, "coerceBoolean");
      return this;
    }

    /** Sets whether to coerce string numbers to BigDecimal. */
    public Builder coerceNumber(boolean coerceNumber) {
      this.coerceNumber = coerceNumber;
      return this;
    }

    /** Sets whether to coerce temporal strings to canonical ISO-8601 format. */
    public Builder coerceTime(boolean coerceTime) {
      this.coerceTime = coerceTime;
      return this;
    }

    /** Sets the default time zone for date-only temporal values. */
    public Builder defaultZoneId(ZoneId defaultZoneId) {
      this.defaultZoneId = Objects.requireNonNull(defaultZoneId, "defaultZoneId");
      return this;
    }

    /** Sets fields where array element order should be preserved (no sorting). */
    public Builder sequenceFieldWhitelist(Set<String> fields) {
      this.sequenceFieldWhitelist.clear();
      if (fields != null) {
        this.sequenceFieldWhitelist.addAll(fields);
      }
      return this;
    }

    /** Sets whether to deduplicate array elements. */
    public Builder arrayDeduplicate(boolean arrayDeduplicate) {
      this.arrayDeduplicate = arrayDeduplicate;
      return this;
    }

    /** Sets whether to trim leading and trailing whitespace from strings. */
    public Builder trimStrings(boolean trimStrings) {
      this.trimStrings = trimStrings;
      return this;
    }

    /** Sets whether to collapse multiple consecutive spaces into single space. */
    public Builder collapseSpaces(boolean collapseSpaces) {
      this.collapseSpaces = collapseSpaces;
      return this;
    }

    /** Sets fields whose string values should be lowercased. */
    public Builder lowercaseFields(Set<String> fields) {
      this.lowercaseFields.clear();
      if (fields != null) {
        this.lowercaseFields.addAll(fields);
      }
      return this;
    }

    /** Sets the comparator for sorting JSON object keys. */
    public Builder sortComparator(SortComparator sortComparator) {
      this.sortComparator = Objects.requireNonNull(sortComparator, "sortComparator");
      return this;
    }

    /** Sets the maximum nesting depth allowed in JSON structures. */
    public Builder maxDepth(int maxDepth) {
      if (maxDepth <= 0) {
        throw new IllegalArgumentException("maxDepth must be > 0");
      }
      this.maxDepth = maxDepth;
      return this;
    }

    /** Sets the maximum byte length for string values (0 to disable). */
    public Builder maxStringBytes(int maxStringBytes) {
      if (maxStringBytes < 0) {
        throw new IllegalArgumentException("maxStringBytes must be >= 0");
      }
      this.maxStringBytes = maxStringBytes;
      return this;
    }

    /** Sets keys that are forbidden and will cause normalization to fail if encountered. */
    public Builder forbidKeys(Set<String> keys) {
      this.forbidKeys.clear();
      if (keys != null) {
        this.forbidKeys.addAll(keys);
      }
      return this;
    }

    /** Builds the configuration instance. */
    public JsonNormalizerConfig build() {
      return new JsonNormalizerConfig(this);
    }
  }
}
