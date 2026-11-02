package dev.patra.analytics.simulator.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import tools.jackson.databind.json.JsonMapper;

/// 流量模型的全部参数（spec §5）。默认值在资源 `simulator/traffic-model.json`，`--model` 可换一份。
///
/// @param zipfExponent 文献热度的 Zipf 指数
/// @param maxArticles 文献池最多取几篇
/// @param devices 设备数（一人一台）
/// @param registeredUsers 其中注册用户数
/// @param registeredActivityFactor 注册用户的访问频度是访客的几倍
/// @param activityParetoAlpha 访问频度的帕累托指数
/// @param hourly 北京时间每小时的到达率系数（加载后归一化成均值 1）
/// @param weekday 周一到周日的系数
/// @param entry 入口页的概率（`library` 只对登录会话有效）
/// @param transitions 页面之间的转移概率：当前页 → 下一步 → 概率
/// @param dwellMedianSeconds 两页之间停留的中位数
/// @param fulltextDwellMedianSeconds 读全文页停留的中位数
/// @param dwellSigma 停留时间对数正态的 σ
/// @param maxPages 一个会话最多几页
/// @param topicAffinity 后续文献来自兴趣主题的概率
/// @param actions 读全文页与文献库页上的动作概率
/// @param lists 各列表的卡片数
/// @param crawlers 爬虫与无脚本访客
/// @param regions 地区分布
/// @param userAgents UA 分布（含声明身份的爬虫 UA）
/// @param referrers 从外部直达文献页时的来源域名
/// @param searchTerms 检索词表
/// @param zeroResultRate 检索无结果的比例
@Builder(toBuilder = true)
public record TrafficModel(
    double zipfExponent,
    int maxArticles,
    int devices,
    int registeredUsers,
    double registeredActivityFactor,
    double activityParetoAlpha,
    List<Double> hourly,
    List<Double> weekday,
    Map<String, Double> entry,
    Map<String, Map<String, Double>> transitions,
    double dwellMedianSeconds,
    double fulltextDwellMedianSeconds,
    double dwellSigma,
    int maxPages,
    double topicAffinity,
    Actions actions,
    Lists lists,
    Crawlers crawlers,
    List<Region> regions,
    List<UaProfile> userAgents,
    List<String> referrers,
    List<String> searchTerms,
    double zeroResultRate) {

  private static final String BUILTIN = "/simulator/traffic-model.json";

  /// 防御性拷贝。
  public TrafficModel {
    hourly = List.copyOf(hourly);
    weekday = List.copyOf(weekday);
    entry = Map.copyOf(entry);
    transitions = Map.copyOf(transitions);
    regions = List.copyOf(regions);
    userAgents = List.copyOf(userAgents);
    referrers = List.copyOf(referrers);
    searchTerms = List.copyOf(searchTerms);
  }

  /// 读模型参数并把每小时系数归一化成均值 1。
  ///
  /// @param file 参数文件；`null` 用内置默认值
  /// @param jsonMapper JSON
  /// @return 模型
  public static TrafficModel load(Path file, JsonMapper jsonMapper) {
    try (InputStream in =
        file != null
            ? Files.newInputStream(file)
            : TrafficModel.class.getResourceAsStream(BUILTIN)) {
      TrafficModel raw = jsonMapper.readValue(in, TrafficModel.class);
      return raw.normalized();
    } catch (IOException e) {
      throw new UncheckedIOException("读流量模型失败：" + file, e);
    }
  }

  /// 校验并归一化：每小时系数均值为 1；转移概率每行、入口概率之和为 1。
  ///
  /// @return 新模型
  private TrafficModel normalized() {
    if (hourly.size() != 24 || weekday.size() != 7) {
      throw new IllegalArgumentException("hourly 要 24 个、weekday 要 7 个");
    }
    requireSumOne("entry", entry);
    transitions.forEach((page, row) -> requireSumOne("transitions." + page, row));
    double mean = hourly.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    return toBuilder().hourly(hourly.stream().map(h -> h / mean).toList()).build();
  }

  /// 概率之和必须为 1。
  ///
  /// @param name 参数名
  /// @param row 概率
  private static void requireSumOne(String name, Map<String, Double> row) {
    double sum = row.values().stream().mapToDouble(Double::doubleValue).sum();
    if (Math.abs(sum - 1.0) > 1e-9) {
      throw new IllegalArgumentException(name + " 的概率之和是 " + sum + "，要等于 1");
    }
  }

  /// 读全文页与文献库页上的动作概率。
  ///
  /// @param pdfAfterFulltext 读全文后下载 PDF
  /// @param citationAfterFulltext 读全文后复制引用
  /// @param collectAfterFulltext 读全文后收藏（只对登录用户）
  /// @param exportInLibrary 在文献库页导出
  public record Actions(
      double pdfAfterFulltext,
      double citationAfterFulltext,
      double collectAfterFulltext,
      double exportInLibrary) {}

  /// 各列表一次渲染出几张卡片。
  ///
  /// @param feedMin 首页文献流最少
  /// @param feedMax 首页文献流最多
  /// @param searchMin 检索结果一页最少曝光
  /// @param searchMax 检索结果一页最多曝光
  /// @param related 「看过还看了」的卡片数
  /// @param relatedSeenRate 读者滚到「看过还看了」、卡片满足曝光规则的比例
  public record Lists(
      int feedMin,
      int feedMax,
      int searchMin,
      int searchMax,
      int related,
      double relatedSeenRate) {}

  /// 爬虫与无脚本访客（spec §5.5）。
  ///
  /// @param declared 声明身份的爬虫
  /// @param stealth 伪装成浏览器的爬虫
  /// @param noscript 不跑脚本的访客
  public record Crawlers(CrawlerSpec declared, CrawlerSpec stealth, CrawlerSpec noscript) {}

  /// 一类非真人会话的参数。
  ///
  /// @param share 占会话的份额
  /// @param minPages 最少几页
  /// @param maxPages 最多几页
  /// @param minIntervalSeconds 两页之间最短间隔
  /// @param maxIntervalSeconds 两页之间最长间隔
  public record CrawlerSpec(
      double share,
      int minPages,
      int maxPages,
      double minIntervalSeconds,
      double maxIntervalSeconds) {}
}
