package dev.patra.analytics.simulator.model;

import dev.patra.analytics.simulator.event.SyntheticIds;
import java.util.List;
import java.util.SplittableRandom;

/// 人群（spec §5.2）：固定数量的设备，其中一部分是注册用户；每人的访问频度服从帕累托分布（少数常客、多数偶来）。
///
/// 同一个种子建出的人群完全相同，所以灌历史的各块、各天抽到的是同一批人。
public final class Population {

  /// 单人访问频度相对最小值的上限：不封顶的话帕累托尾巴会造出一天几百个会话的「人」
  private static final double ACTIVITY_CAP = 50;

  private final Person[] persons;
  private final WeightedSampler activity;
  private final List<UaProfile> humanUas;
  private final List<UaProfile> crawlerUas;
  private final WeightedSampler humanUaSampler;
  private final WeightedSampler crawlerUaSampler;
  private final List<Region> regions;
  private final WeightedSampler regionSampler;
  private final SyntheticIds ids;

  private Population(TrafficModel model, SyntheticIds ids, long seed) {
    this.ids = ids;
    this.humanUas = model.userAgents().stream().filter(ua -> !ua.crawler()).toList();
    this.crawlerUas = model.userAgents().stream().filter(UaProfile::crawler).toList();
    this.humanUaSampler = weighted(humanUas.stream().mapToDouble(UaProfile::weight).toArray());
    this.crawlerUaSampler = weighted(crawlerUas.stream().mapToDouble(UaProfile::weight).toArray());
    this.regions = model.regions();
    this.regionSampler = weighted(regions.stream().mapToDouble(Region::weight).toArray());
    SplittableRandom random = new SplittableRandom(seed);
    this.persons = new Person[model.devices()];
    double[] weights = new double[persons.length];
    for (int i = 0; i < persons.length; i++) {
      boolean registered = i < model.registeredUsers();
      persons[i] =
          new Person(
              i,
              ids.deviceId(random),
              registered ? ids.userId(i) : null,
              humanUas.get(humanUaSampler.sample(random)),
              regions.get(regionSampler.sample(random)));
      double pareto =
          Math.min(
              ACTIVITY_CAP, Math.pow(1 - random.nextDouble(), -1 / model.activityParetoAlpha()));
      weights[i] = registered ? pareto * model.registeredActivityFactor() : pareto;
    }
    this.activity = new WeightedSampler(weights);
  }

  /// 建人群。
  ///
  /// @param model 流量模型
  /// @param ids 本次运行的标识规则
  /// @param seed 种子
  /// @return 人群
  public static Population build(TrafficModel model, SyntheticIds ids, long seed) {
    return new Population(model, ids, seed);
  }

  /// 空列表时给一个永不被调用的占位分布，避免模型里没有爬虫 UA 时建不起来。
  ///
  /// @param weights 权重
  /// @return 分布
  private static WeightedSampler weighted(double[] weights) {
    return new WeightedSampler(weights.length == 0 ? new double[] {1} : weights);
  }

  /// 人数（不含爬虫的一次性设备）。
  ///
  /// @return 人数
  public int size() {
    return persons.length;
  }

  /// 第 `index` 个人。
  ///
  /// @param index 下标
  /// @return 人
  public Person person(int index) {
    return persons[index];
  }

  /// 按访问频度抽一个人来开会话。
  ///
  /// @param random 随机源
  /// @return 人
  public Person sample(SplittableRandom random) {
    return persons[activity.sample(random)];
  }

  /// 造一台一次性设备（爬虫不存 Cookie，每次都是新设备）：访客、随机地区。
  ///
  /// @param index 下标，调用方保证不与人群及其他一次性设备重叠（从 `size()` 起）
  /// @param crawlerUa `true` 用声明身份的爬虫 UA，`false` 用普通浏览器 UA（伪装爬虫、无脚本访客）
  /// @param random 随机源
  /// @return 人
  public Person freshDevice(int index, boolean crawlerUa, SplittableRandom random) {
    UaProfile ua =
        crawlerUa
            ? crawlerUas.get(crawlerUaSampler.sample(random))
            : humanUas.get(humanUaSampler.sample(random));
    return new Person(
        index, ids.deviceId(random), null, ua, regions.get(regionSampler.sample(random)));
  }
}
