package dev.patra.analytics.simulator.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/// 文献池：catalog 里真实存在的文献 ID（spec §5.1），文件顺序即热度排名（导出时已随机）。
///
/// 热度按 Zipf 分布；按刊（`venue_id`）分主题，主题内仍按全局排名的相对次序服从 Zipf，主题的热度是其文献概率之和。
public final class ArticlePool {

  private final long[] ids;
  private final int[] topics;
  private final WeightedSampler global;
  private final WeightedSampler topicSampler;
  private final int[][] topicMembers;
  private final WeightedSampler[] inTopic;

  private ArticlePool(long[] ids, long[] venues, double exponent) {
    this.ids = ids;
    this.topics = new int[ids.length];
    Map<Long, Integer> topicOfVenue = new HashMap<>();
    List<List<Integer>> members = new ArrayList<>();
    for (int i = 0; i < ids.length; i++) {
      int topic =
          topicOfVenue.computeIfAbsent(
              venues[i],
              venue -> {
                members.add(new ArrayList<>());
                return members.size() - 1;
              });
      topics[i] = topic;
      members.get(topic).add(i);
    }
    double[] weights = ZipfSampler.weights(ids.length, exponent);
    this.global = new WeightedSampler(weights);
    this.topicMembers = new int[members.size()][];
    this.inTopic = new WeightedSampler[members.size()];
    double[] topicWeights = new double[members.size()];
    for (int t = 0; t < members.size(); t++) {
      int[] indexes = members.get(t).stream().mapToInt(Integer::intValue).toArray();
      double[] memberWeights = new double[indexes.length];
      for (int j = 0; j < indexes.length; j++) {
        memberWeights[j] = weights[indexes[j]];
        topicWeights[t] += weights[indexes[j]];
      }
      topicMembers[t] = indexes;
      inTopic[t] = new WeightedSampler(memberWeights);
    }
    this.topicSampler = new WeightedSampler(topicWeights);
  }

  /// 读文献池文件：每行 `id,venue_id`；重复 ID 只留第一次出现的，最多取前 `maxArticles` 个。
  ///
  /// @param csv 文件
  /// @param maxArticles 上限
  /// @return 文献池（Zipf 指数 1.0）
  /// @throws IllegalArgumentException 文件为空或某行不合法（消息带行号）
  public static ArticlePool load(Path csv, int maxArticles) {
    return load(csv, maxArticles, 1.0);
  }

  /// 读文献池文件，指定 Zipf 指数。
  ///
  /// @param csv 文件
  /// @param maxArticles 上限
  /// @param exponent Zipf 指数
  /// @return 文献池
  public static ArticlePool load(Path csv, int maxArticles, double exponent) {
    List<long[]> rows = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
      String line;
      int number = 0;
      while ((line = reader.readLine()) != null && rows.size() < maxArticles) {
        number++;
        if (line.isBlank()) {
          continue;
        }
        long[] row = parse(line, number);
        if (seen.add(row[0])) {
          rows.add(row);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("读文献池失败：" + csv, e);
    }
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("文献池为空：" + csv);
    }
    long[] ids = rows.stream().mapToLong(r -> r[0]).toArray();
    long[] venues = rows.stream().mapToLong(r -> r[1]).toArray();
    return new ArticlePool(ids, venues, exponent);
  }

  /// 解析一行 `id,venue_id`。
  ///
  /// @param line 行
  /// @param number 行号
  /// @return `{id, venueId}`
  private static long[] parse(String line, int number) {
    String[] parts = line.trim().split(",", -1);
    try {
      if (parts.length != 2) {
        throw new NumberFormatException("列数不是 2");
      }
      long id = Long.parseLong(parts[0].trim());
      long venue = Long.parseLong(parts[1].trim());
      if (id <= 0 || venue < 0) {
        throw new NumberFormatException("ID 要是正整数");
      }
      return new long[] {id, venue};
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("文献池第 " + number + " 行不合法：" + line, e);
    }
  }

  /// 文献个数。
  ///
  /// @return 个数
  public int size() {
    return ids.length;
  }

  /// 第 `index` 名的文献 ID。
  ///
  /// @param index 排名，0 起
  /// @return 文献 ID
  public long idAt(int index) {
    return ids[index];
  }

  /// 文献所在主题。
  ///
  /// @param index 排名
  /// @return 主题号
  public int topicOf(int index) {
    return topics[index];
  }

  /// 主题个数。
  ///
  /// @return 个数
  public int topicCount() {
    return topicMembers.length;
  }

  /// 按全局热度抽一篇。
  ///
  /// @param random 随机源
  /// @return 排名
  public int sampleGlobal(SplittableRandom random) {
    return global.sample(random);
  }

  /// 按主题总热度抽一个主题。
  ///
  /// @param random 随机源
  /// @return 主题号
  public int sampleTopic(SplittableRandom random) {
    return topicSampler.sample(random);
  }

  /// 在主题内按热度抽一篇。
  ///
  /// @param topic 主题号
  /// @param random 随机源
  /// @return 排名
  public int sampleInTopic(int topic, SplittableRandom random) {
    return topicMembers[topic][inTopic[topic].sample(random)];
  }

  /// 爬虫线性遍历：第 `step` 步落在哪一篇，走到末尾从头再来。
  ///
  /// @param step 步数
  /// @return 排名
  public int linear(int step) {
    return Math.floorMod(step, ids.length);
  }
}
