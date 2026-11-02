package dev.patra.analytics.simulator.truth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/// 真值与 ClickHouse 的逐行比对，可以一天一天累加（对账时不必把 1 亿条量级的真值整份放进内存）。
///
/// 误差 = Σ|ClickHouse − 真值| ÷ Σ真值，正负不抵消；一边缺的行按 0 算。
final class Comparison {

  private static final int SAMPLE_LIMIT = 10;

  private long truthViewers;
  private long actualViewers;
  private long truthFulltext;
  private long actualFulltext;
  private long viewerDiff;
  private long fulltextDiff;
  private int mismatched;
  private final List<String> samples = new ArrayList<>();

  /// 累加一批（通常是一天）。
  ///
  /// @param truth 真值
  /// @param actual ClickHouse 算出的
  /// @return 自身
  Comparison add(List<TruthRow> truth, List<TruthRow> actual) {
    Map<String, TruthRow> expected = index(truth);
    Map<String, TruthRow> found = index(actual);
    TreeSet<String> keys = new TreeSet<>(expected.keySet());
    keys.addAll(found.keySet());
    for (String key : keys) {
      TruthRow e = expected.get(key);
      TruthRow a = found.get(key);
      int ev = e != null ? e.viewers() : 0;
      int ef = e != null ? e.fulltextReaders() : 0;
      int av = a != null ? a.viewers() : 0;
      int af = a != null ? a.fulltextReaders() : 0;
      truthViewers += ev;
      truthFulltext += ef;
      actualViewers += av;
      actualFulltext += af;
      viewerDiff += Math.abs(av - ev);
      fulltextDiff += Math.abs(af - ef);
      if (ev != av || ef != af) {
        mismatched++;
        if (samples.size() < SAMPLE_LIMIT) {
          samples.add(key + " 真值 " + ev + "/" + ef + "，ClickHouse " + av + "/" + af);
        }
      }
    }
    return this;
  }

  /// 汇总（不含事件条数；`passed` 只看两个误差）。
  ///
  /// @return 结果
  ReconcileResult result() {
    double viewersError = ratio(viewerDiff, truthViewers);
    double fulltextError = ratio(fulltextDiff, truthFulltext);
    return ReconcileResult.builder()
        .truthViewers(truthViewers)
        .actualViewers(actualViewers)
        .viewersError(viewersError)
        .truthFulltext(truthFulltext)
        .actualFulltext(actualFulltext)
        .fulltextError(fulltextError)
        .mismatchedRows(mismatched)
        .sampleMismatches(samples)
        .passed(viewersError <= Reconciler.TOLERANCE && fulltextError <= Reconciler.TOLERANCE)
        .build();
  }

  /// 按（天，文献）建索引。
  ///
  /// @param rows 行
  /// @return 键 → 行
  private static Map<String, TruthRow> index(List<TruthRow> rows) {
    Map<String, TruthRow> map = new TreeMap<>();
    rows.forEach(row -> map.put(row.day() + "#" + row.articleId(), row));
    return map;
  }

  /// 差值占比；分母为 0 时，有差即 100%。
  ///
  /// @param diff 差值之和
  /// @param total 真值之和
  /// @return 比例
  private static double ratio(long diff, long total) {
    if (total == 0) {
      return diff == 0 ? 0 : 1;
    }
    return diff / (double) total;
  }
}
