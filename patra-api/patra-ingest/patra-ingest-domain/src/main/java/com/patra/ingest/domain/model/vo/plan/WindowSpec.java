package com.patra.ingest.domain.model.vo.plan;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.util.Map;

/// 窗口规范值对象密封接口,表示数据采集窗口的边界规范。
/// 
/// 密封继承层次确保编译时完备性检查。支持多种数据采集分区策略:
/// 
/// - TIME - 基于时间的窗口(from/to 时间戳)
///   - ID_RANGE - 基于ID范围的窗口(from/to ID)
///   - CURSOR_LANDMARK - 基于游标/分页的窗口
///   - VOLUME_BUDGET - 基于容量预算的窗口
///   - SINGLE - 单一窗口(无分区)
/// 
/// 不可变性:所有实现都是不可变的Record
/// 
/// 使用场景:在任务规划阶段指定数据采集的窗口边界
/// 
/// @author linqibin
/// @since 0.1.0
public sealed interface WindowSpec
    permits WindowSpec.Time,
        WindowSpec.IdRange,
        WindowSpec.CursorLandmark,
        WindowSpec.VolumeBudget,
        WindowSpec.Single {

  /// 获取窗口规范的策略类型。
/// 
/// @return 切片策略枚举
  SliceStrategy strategy();

  /// 转换为可JSON序列化的Map,用于持久化层存储。
/// 
/// Map结构根据策略类型变化(格式B - 嵌套JSON):
/// 
/// - TIME:
///       {"strategy":"TIME","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z","boundary":{"from":"CLOSED","to":"OPEN"},"timezone":"UTC"}}
///   - ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}
///   - CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}
///   - VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}
///   - SINGLE: {"strategy":"SINGLE"}
/// 
/// @return 包含策略代码和策略特定字段的JSON可序列化Map
  Map<String, Object> toMap();

  // ============ 策略实现 ============

  /// 基于时间的窗口规范值对象。
/// 
/// 业务约束:
/// 
/// - from和to必须非空
///   - from必须早于或等于to
/// 
/// @param from 起始时间戳(闭区间)
/// @param to 结束时间戳(开区间)
  record Time(Instant from, Instant to) implements WindowSpec {
    public Time {
      if (from == null || to == null) {
        throw new IllegalArgumentException("时间窗口要求from和to都非空");
      }
      if (from.isAfter(to)) {
        throw new IllegalArgumentException("from必须早于或等于to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.TIME;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> boundaryMap =
          Map.of(
              "from", "CLOSED",
              "to", "OPEN");
      Map<String, Object> windowMap =
          Map.of(
              "from",
              from.toString(),
              "to",
              to.toString(),
              "boundary",
              boundaryMap,
              "timezone",
              "UTC");
      return Map.of("strategy", SliceStrategy.TIME.getCode(), "window", windowMap);
    }
  }

  /// 基于ID范围的窗口规范值对象。
/// 
/// 业务约束:
/// 
/// - from和to必须非空
///   - from必须小于或等于to
/// 
/// @param from 起始ID(闭区间)
/// @param to 结束ID(闭区间)
  record IdRange(Long from, Long to) implements WindowSpec {
    public IdRange {
      if (from == null || to == null) {
        throw new IllegalArgumentException("ID范围窗口要求from和to都非空");
      }
      if (from > to) {
        throw new IllegalArgumentException("from必须小于或等于to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.ID_RANGE;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap =
          Map.of(
              "from", from,
              "to", to);
      return Map.of("strategy", SliceStrategy.ID_RANGE.getCode(), "window", windowMap);
    }
  }

  /// 基于游标/地标的窗口规范值对象,用于分页场景。
/// 
/// 业务约束:
/// 
/// - from和to必须非空且非空白
/// 
/// @param from 起始游标/令牌
/// @param to 结束游标/令牌
  record CursorLandmark(String from, String to) implements WindowSpec {
    public CursorLandmark {
      if (from == null || to == null || from.isBlank() || to.isBlank()) {
        throw new IllegalArgumentException("游标地标窗口要求from和to都非空且非空白");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.CURSOR_LANDMARK;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap =
          Map.of(
              "from", from,
              "to", to);
      return Map.of("strategy", SliceStrategy.CURSOR_LANDMARK.getCode(), "window", windowMap);
    }
  }

  /// 基于容量预算的窗口规范值对象。
/// 
/// 业务约束:
/// 
/// - limit必须为正数
///   - unit必须非空且非空白
/// 
/// @param limit 最大容量/数量
/// @param unit 度量单位(例如:"RECORDS","BYTES","MB")
  record VolumeBudget(Integer limit, String unit) implements WindowSpec {
    public VolumeBudget {
      if (limit == null || limit <= 0) {
        throw new IllegalArgumentException("容量限制必须为正数");
      }
      if (unit == null || unit.isBlank()) {
        throw new IllegalArgumentException("容量单位必须非空");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.VOLUME_BUDGET;
    }

    @Override
    public Map<String, Object> toMap() {
      return Map.of(
          "strategy", SliceStrategy.VOLUME_BUDGET.getCode(),
          "limit", limit,
          "unit", unit);
    }
  }

  /// 单一窗口规范值对象(无分区)。
/// 
/// 使用场景:不需要窗口分区的简单采集场景
  record Single() implements WindowSpec {
    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.SINGLE;
    }

    @Override
    public Map<String, Object> toMap() {
      return Map.of("strategy", SliceStrategy.SINGLE.getCode());
    }
  }

  // ============ 工厂方法 ============

  /// 创建基于时间的窗口规范。
/// 
/// @param from 起始时间戳
/// @param to 结束时间戳
/// @return 时间窗口规范
  static Time ofTime(Instant from, Instant to) {
    return new Time(from, to);
  }

  /// 创建基于ID范围的窗口规范。
/// 
/// @param from 起始ID
/// @param to 结束ID
/// @return ID范围窗口规范
  static IdRange ofIdRange(Long from, Long to) {
    return new IdRange(from, to);
  }

  /// 创建基于游标的窗口规范。
/// 
/// @param from 起始游标
/// @param to 结束游标
/// @return 游标窗口规范
  static CursorLandmark ofCursor(String from, String to) {
    return new CursorLandmark(from, to);
  }

  /// 创建基于容量预算的窗口规范。
/// 
/// @param limit 最大容量
/// @param unit 度量单位
/// @return 容量预算窗口规范
  static VolumeBudget ofVolume(Integer limit, String unit) {
    return new VolumeBudget(limit, unit);
  }

  /// 创建单一窗口规范(无分区)。
/// 
/// @return 单一窗口规范
  static Single ofSingle() {
    return new Single();
  }

  /// 从持久化Map重建WindowSpec(供基础设施层使用)。
/// 
/// 期望的Map格式(格式B - 嵌套JSON):
/// 
/// - TIME:
///       {"strategy":"TIME","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z",...}}
///   - ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}
///   - CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}
///   - VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}
///   - SINGLE: {"strategy":"SINGLE"}
/// 
/// @param map JSON反序列化的Map,至少包含"strategy"键
/// @return 重建的WindowSpec实例
/// @throws IllegalArgumentException 如果map为null、空、结构无效或包含未知策略
  static WindowSpec fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      throw new IllegalArgumentException("窗口规范map不能为null或空");
    }

    SliceStrategy strategy = extractAndValidateStrategy(map);

    return switch (strategy) {
      case TIME -> parseTimeWindow(map, "TIME");
      case DATE -> parseTimeWindow(map, "DATE");
      case ID_RANGE -> parseIdRangeWindow(map);
      case CURSOR_LANDMARK -> parseCursorLandmarkWindow(map);
      case VOLUME_BUDGET -> parseVolumeBudgetWindow(map);
      case SINGLE -> new Single();
      case HYBRID -> throw new UnsupportedOperationException("HYBRID策略尚未实现");
    };
  }

  /// 从map中提取并验证策略代码。
/// 
/// @param map 窗口规范map
/// @return 已验证的切片策略枚举
/// @throws IllegalArgumentException 如果策略缺失、类型无效或代码未知
  private static SliceStrategy extractAndValidateStrategy(Map<String, Object> map) {
    Object strategyObj = map.get("strategy");
    if (strategyObj == null) {
      throw new IllegalArgumentException("窗口规范map必须包含'strategy'键");
    }
    if (!(strategyObj instanceof String)) {
      throw new IllegalArgumentException(
          "窗口规范'strategy'必须是字符串, 实际类型: " + strategyObj.getClass().getSimpleName());
    }

    String strategyCode = (String) strategyObj;
    return SliceStrategy.fromCode(strategyCode)
        .orElseThrow(() -> new IllegalArgumentException("未知的切片策略代码: '" + strategyCode + "'"));
  }

  /// 从map中解析TIME或DATE窗口。
/// 
/// @param map 窗口规范map
/// @param strategyName 策略名称(用于错误消息)
/// @return 时间窗口规范
  private static Time parseTimeWindow(Map<String, Object> map, String strategyName) {
    Map<String, Object> windowMap = extractRequiredWindowMap(map, strategyName);
    Object fromObj = windowMap.get("from");
    Object toObj = windowMap.get("to");

    if (fromObj == null || toObj == null) {
      throw new IllegalArgumentException(strategyName + " 窗口要求'from'和'to'字段都存在");
    }
    if (!(fromObj instanceof String) || !(toObj instanceof String)) {
      throw new IllegalArgumentException(strategyName + " 窗口'from'和'to'必须是ISO-8601时间戳字符串");
    }

    return new Time(Instant.parse((String) fromObj), Instant.parse((String) toObj));
  }

  /// 从map中解析ID_RANGE窗口。
/// 
/// @param map 窗口规范map
/// @return ID范围窗口规范
  private static IdRange parseIdRangeWindow(Map<String, Object> map) {
    Map<String, Object> windowMap = extractRequiredWindowMap(map, "ID_RANGE");
    Object fromObj = windowMap.get("from");
    Object toObj = windowMap.get("to");

    if (fromObj == null || toObj == null) {
      throw new IllegalArgumentException("ID_RANGE 窗口要求'from'和'to'字段都存在");
    }
    if (!(fromObj instanceof Number) || !(toObj instanceof Number)) {
      throw new IllegalArgumentException("ID_RANGE 窗口'from'和'to'必须是数值");
    }

    return new IdRange(((Number) fromObj).longValue(), ((Number) toObj).longValue());
  }

  /// 从map中解析CURSOR_LANDMARK窗口。
/// 
/// @param map 窗口规范map
/// @return 游标地标窗口规范
  private static CursorLandmark parseCursorLandmarkWindow(Map<String, Object> map) {
    Map<String, Object> windowMap = extractRequiredWindowMap(map, "CURSOR_LANDMARK");
    Object fromObj = windowMap.get("from");
    Object toObj = windowMap.get("to");

    if (fromObj == null || toObj == null) {
      throw new IllegalArgumentException("CURSOR_LANDMARK 窗口要求'from'和'to'字段都存在");
    }
    if (!(fromObj instanceof String) || !(toObj instanceof String)) {
      throw new IllegalArgumentException("CURSOR_LANDMARK 窗口'from'和'to'必须是字符串值");
    }

    return new CursorLandmark((String) fromObj, (String) toObj);
  }

  /// 从map中解析VOLUME_BUDGET窗口。
/// 
/// @param map 窗口规范map
/// @return 容量预算窗口规范
  private static VolumeBudget parseVolumeBudgetWindow(Map<String, Object> map) {
    Object limitObj = map.get("limit");
    Object unitObj = map.get("unit");

    if (limitObj == null || unitObj == null) {
      throw new IllegalArgumentException("VOLUME_BUDGET 策略要求'limit'和'unit'字段都存在");
    }
    if (!(limitObj instanceof Number)) {
      throw new IllegalArgumentException("VOLUME_BUDGET 'limit'必须是数值");
    }
    if (!(unitObj instanceof String)) {
      throw new IllegalArgumentException("VOLUME_BUDGET 'unit'必须是字符串值");
    }

    return new VolumeBudget(((Number) limitObj).intValue(), (String) unitObj);
  }

  /// 从主map中提取并验证'window'子map。
/// 
/// @param map 主窗口规范map
/// @param strategyName 策略名称(用于错误消息)
/// @return 提取的window子map
/// @throws IllegalArgumentException 如果'window'缺失或不是map
  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractRequiredWindowMap(
      Map<String, Object> map, String strategyName) {
    Object windowObj = map.get("window");
    if (windowObj == null) {
      throw new IllegalArgumentException(strategyName + " 策略要求'window'对象存在");
    }
    if (!(windowObj instanceof Map)) {
      throw new IllegalArgumentException(
          strategyName + " 'window'必须是JSON对象, 实际类型: " + windowObj.getClass().getSimpleName());
    }
    return (Map<String, Object>) windowObj;
  }
}
