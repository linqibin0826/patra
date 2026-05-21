package dev.linqibin.patra.starter.provenance.common.config;

/// 时间窗口偏移配置记录
///
/// 定义增量数据采集时使用的滑动时间窗口策略,包括窗口大小、回溯时间、窗口重叠等参数。 用于支持定期调度场景下的增量数据抓取,避免遗漏或重复采集数据。
///
/// @param windowModeCode 滑动窗口选择策略标识符
/// @param windowSizeValue 增量运行时使用的窗口大小数值
/// @param windowSizeUnitCode 窗口大小的时间单位(如 DAY、WEEK)
/// @param lookbackValue 调度窗口时回溯的单位数量
/// @param lookbackUnitCode 回溯值的时间单位
/// @param overlapValue 连续窗口之间的重叠量,用于避免数据间隙
/// @param overlapUnitCode 重叠量的时间单位
/// @param offsetTypeCode 偏移类型提示,由调度逻辑消费
/// @param maxIdsPerWindow 每个窗口处理的标识符数量上限
/// @author linqibin
/// @since 0.1.0
public record WindowOffsetConfig(
    String windowModeCode,
    Integer windowSizeValue,
    String windowSizeUnitCode,
    Integer lookbackValue,
    String lookbackUnitCode,
    Integer overlapValue,
    String overlapUnitCode,
    String offsetTypeCode,
    Integer maxIdsPerWindow) {}
