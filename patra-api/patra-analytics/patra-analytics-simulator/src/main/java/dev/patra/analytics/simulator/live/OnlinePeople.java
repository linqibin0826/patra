package dev.patra.analytics.simulator.live;

import dev.patra.analytics.simulator.model.Person;
import dev.patra.analytics.simulator.model.Population;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/// 正在线的人（spec §9）：同一个人不同时开两个会话，否则同一设备的文献浏览叠加，会把采集端点的频率规则顶穿、
/// 真人被误判成爬虫。抽到在线的人就重抽，连续抽不到就给一台一次性设备。
public final class OnlinePeople {

  private static final int ATTEMPTS = 100;

  private final Population population;
  private final Set<Integer> busy = ConcurrentHashMap.newKeySet();
  private final AtomicInteger fresh;

  /// 创建。
  ///
  /// @param population 人群
  public OnlinePeople(Population population) {
    this.population = population;
    this.fresh = new AtomicInteger(population.size());
  }

  /// 抽一个不在线的人并标为在线。
  ///
  /// @param random 随机源
  /// @return 人
  public Person acquire(SplittableRandom random) {
    for (int i = 0; i < ATTEMPTS; i++) {
      Person person = population.sample(random);
      if (busy.add(person.index())) {
        return person;
      }
    }
    Person person = population.freshDevice(nextFreshIndex(), false, random);
    busy.add(person.index());
    return person;
  }

  /// 下线。
  ///
  /// @param person 人
  public void release(Person person) {
    busy.remove(person.index());
  }

  /// 下一个一次性设备的下标（与爬虫共用，不重叠）。
  ///
  /// @return 下标
  public int nextFreshIndex() {
    return fresh.getAndIncrement();
  }

  /// 在线人数。
  ///
  /// @return 人数
  public int size() {
    return busy.size();
  }
}
