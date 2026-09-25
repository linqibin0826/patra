package dev.linqibin.patra.catalog.domain.model.vo.publication;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// 文献证据等级（基于出版类型 MeSH 词汇衍生的领域值对象）。
///
/// 对应前端证据徽章 5 档；[#classify] 由 `cat_publication_type.type_value` 列表推算，
/// 多类型取最强档，无法判定返回 [#UNKNOWN]（降级态，绝不臆造）。映射依据设计规格 §6。
///
/// @author linqibin
/// @since 0.1.0
public enum EvidenceLevel {

  /// 系统综述 / Meta 分析（最强）。
  SYSTEMATIC_REVIEW(5, "系统综述 / Meta 分析"),
  /// 随机对照试验。
  RANDOMIZED_CONTROLLED_TRIAL(4, "随机对照试验"),
  /// 队列 / 病例对照 / 观察性研究。
  COHORT_OR_CASE_CONTROL(3, "队列 / 病例对照"),
  /// 非系统综述 / 临床试验（非 RCT）/ 指南。
  NON_SYSTEMATIC_REVIEW(2, "非系统综述 / 临床研究"),
  /// 病例报告。
  CASE_REPORT(1, "病例报告"),
  /// 无法判定（降级态）。
  UNKNOWN(0, "未分级");

  private final int rank;
  private final String label;

  EvidenceLevel(int rank, String label) {
    this.rank = rank;
    this.label = label;
  }

  /// 等级权重（越大越强；UNKNOWN 为 0）。
  public int rank() {
    return rank;
  }

  /// 中文展示标签。
  public String label() {
    return label;
  }

  /// 是否为已成功衍生的等级（UNKNOWN 之外）。前端据此显示「衍生」标记或降级态。
  public boolean isDerived() {
    return this != UNKNOWN;
  }

  /// 规范化 type_value（小写）→ 等级映射，覆盖 cat_publication_type 实际枚举。
  private static final Map<String, EvidenceLevel> TYPE_TO_LEVEL =
      Map.ofEntries(
          Map.entry("systematic review", SYSTEMATIC_REVIEW),
          Map.entry("meta-analysis", SYSTEMATIC_REVIEW),
          Map.entry("network meta-analysis", SYSTEMATIC_REVIEW),
          Map.entry("randomized controlled trial", RANDOMIZED_CONTROLLED_TRIAL),
          Map.entry("pragmatic clinical trial", RANDOMIZED_CONTROLLED_TRIAL),
          Map.entry("equivalence trial", RANDOMIZED_CONTROLLED_TRIAL),
          Map.entry("observational study", COHORT_OR_CASE_CONTROL),
          Map.entry("comparative study", COHORT_OR_CASE_CONTROL),
          Map.entry("multicenter study", COHORT_OR_CASE_CONTROL),
          Map.entry("twin study", COHORT_OR_CASE_CONTROL),
          Map.entry("review", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical trial", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical study", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical trial, phase i", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical trial, phase ii", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical trial, phase iii", NON_SYSTEMATIC_REVIEW),
          Map.entry("clinical trial, phase iv", NON_SYSTEMATIC_REVIEW),
          Map.entry("practice guideline", NON_SYSTEMATIC_REVIEW),
          Map.entry("consensus statement", NON_SYSTEMATIC_REVIEW),
          Map.entry("scoping review", NON_SYSTEMATIC_REVIEW),
          Map.entry("case reports", CASE_REPORT));

  /// 由出版类型词汇列表衍生证据等级（取最强档）。
  ///
  /// @param typeValues `cat_publication_type.type_value` 列表（可空；含 null 元素时静默跳过）
  /// @return 最强证据等级；无命中返回 [#UNKNOWN]
  public static EvidenceLevel classify(Collection<String> typeValues) {
    if (typeValues == null || typeValues.isEmpty()) {
      return UNKNOWN;
    }
    EvidenceLevel best = UNKNOWN;
    for (String typeValue : typeValues) {
      if (typeValue == null) {
        continue;
      }
      EvidenceLevel level =
          TYPE_TO_LEVEL.getOrDefault(typeValue.trim().toLowerCase(Locale.ROOT), UNKNOWN);
      if (level.rank > best.rank) {
        best = level;
      }
    }
    return best;
  }

  /// 返回映射到指定等级的全部规范化（小写）类型值；[#UNKNOWN] 返回空集。
  ///
  /// 供读适配器把映射表以数组参数传入 SQL，保证过滤 / 计数与 [#classify] 同源。
  ///
  /// @param level 证据等级
  /// @return 该等级对应的小写 type_value 集合（不可变）
  public static Set<String> typeValuesOf(EvidenceLevel level) {
    return TYPE_TO_LEVEL.entrySet().stream()
        .filter(e -> e.getValue() == level)
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }
}
