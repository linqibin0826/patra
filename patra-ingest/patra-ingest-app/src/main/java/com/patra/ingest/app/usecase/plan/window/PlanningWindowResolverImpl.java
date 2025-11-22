package com.patra.ingest.app.usecase.plan.window;

import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.alignFloor;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.computeLaggedNow;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.isCalendarMode;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.maxInstant;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.minInstant;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveDuration;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveWindowSize;
import static com.patra.ingest.app.usecase.plan.window.PlanningWindowSupport.resolveZone;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 默认的规划窗口解析器实现(HARVEST / BACKFILL / UPDATE)。
/// 
/// 专注于"计划级别"窗口(非切片阶段)。不处理切片重叠或多游标高级策略。
/// 
/// #### 通用规则
/// 
/// - nowSafe = min(currentTime - watermarkLag, currentTime) (配置缺失时不减)
///   - 窗口是半开区间 [from, to)。日历对齐后,如果 from == to,视为空窗口
///   - 如果无法确定模式或不支持,返回 full() 以便上游决定是否全量扫描
///   - 最小非空长度: 如果跨度小于 1s,扩展到 1s 以避免下游任务长度为零
/// 
/// #### HARVEST(采集模式)
/// 
/// ```
/// 
/// toCandidate   = min(user.to?, nowSafe)
/// fromCandidate = harvestWM? max(user.from?, harvestWM - lookback) : (user.from? | toCandidate - windowSize)
/// CALENDAR 对齐 -> 空窗口检查
/// 
/// ```
/// 
/// #### BACKFILL(回填模式)
/// 
/// ```
/// 
/// upperAnchor   = min(user.to?, forwardWM?, nowSafe)  // forwardWM 保留,暂未注入
/// fromCandidate = backfillWM? max(backfillWM, user.from?) : (user.from? | upperAnchor - windowSize)
/// 边界修正: fromCandidate 不得 > upperAnchor
/// CALENDAR 对齐 -> 空窗口检查
/// 
/// ```
/// 
/// #### UPDATE(更新模式)
/// 
/// ```
/// 
/// timeDriven = (offsetType=DATE 且用户窗口存在) 或 (提供了任何用户窗口)
/// if timeDriven:
///   toCandidate   = min(user.to?, nowSafe) (默认为 nowSafe)
///   fromCandidate = if updateWM and user.from? -> max(updateWM, user.from?)
///                |  仅 updateWM           -> updateWM (然后与 user.from? 比较)
///                |  仅 user.from          -> user.from
///                |  否则                   -> nowSafe - windowSize
/// else (ID驱动):
///   if 用户窗口存在: toCandidate = min(user.to?, nowSafe) (默认为 nowSafe)
///                    fromCandidate = user.from? | (toCandidate - windowSize)
///   else:            [nowSafe - windowSize, nowSafe]
/// CALENDAR 对齐 -> 空窗口检查
/// 
/// ```
/// 
/// #### 设计权衡
/// 
/// - forwardWM 暂未暴露;需要时可通过接口扩展或嵌入 triggerNorm 中
///   - maxWindowSpanSeconds 在此未硬性限制;切片阶段(TimeSlicePlanner)应控制粒度
///   - 对齐后遇到空窗口时,返回"最小有效窗口"而非 null 以简化上游检查; 仍可通过 from==to (加 +1s 扩展) 检测原始空状态
/// 
/// #### 复杂度
/// 
/// 所有分支均为 O(1) 且不执行外部 IO。
/// 
/// #### 线程安全
/// 
/// 无状态;可安全地作为单例复用。
/// 
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class PlanningWindowResolverImpl implements PlanningWindowResolver {

  /// 默认总窗口跨度(24小时)。
  private static final Duration DEFAULT_WINDOW_SIZE = Duration.ofHours(24);

  /// 计算 nowSafe 时用于限制"当前时间"的默认安全延迟。
  private static final Duration DEFAULT_SAFETY_LAG = Duration.ZERO;

  /// 最小有效窗口长度(> 0秒),避免空窗口。
  private static final Duration MIN_EFFECTIVE_WINDOW = Duration.ofSeconds(1);

  /// 解析规划窗口。
/// 
/// 根据触发器规范化信息、配置快照和游标水位线,解析适合当前模式的窗口。
/// 
/// @param triggerNorm 触发器规范化信息(包含模式和用户窗口)
/// @param snapshot Provenance 配置快照
/// @param cursorWatermark 游标水位线(根据模式使用不同的水位线)
/// @param currentTime 当前时间
/// @return 规划窗口
  @Override
  public PlannerWindow resolveWindow(
      PlanTriggerNorm triggerNorm,
      ProvenanceConfigSnapshot snapshot,
      Instant cursorWatermark,
      Instant currentTime) {
    ProvenanceConfigSnapshot.WindowOffsetConfig cfg =
        snapshot == null ? null : snapshot.windowOffset();
    String timezone =
        snapshot != null && snapshot.provenance() != null
            ? snapshot.provenance().timezoneDefault()
            : null;

    Instant userFrom = triggerNorm.requestedWindowFrom();
    Instant userTo = triggerNorm.requestedWindowTo();

    // 使用配置的安全延迟裁剪当前时间,避免采集尚未稳定的数据
    Instant nowSafe = computeLaggedNow(currentTime, cfg, DEFAULT_SAFETY_LAG);

    // 根据模式分发到具体的解析方法
    if (triggerNorm.isHarvest()) {
      return resolveHarvest(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
    } else if (triggerNorm.isBackfill()) {
      return resolveBackfill(cfg, cursorWatermark, null, userFrom, userTo, nowSafe, timezone);
    } else if (triggerNorm.isUpdate()) {
      return resolveUpdate(cfg, cursorWatermark, userFrom, userTo, nowSafe, timezone);
    }
    // 未知模式,返回全量窗口
    return PlannerWindow.full();
  }

  /* ===================== HARVEST(采集模式) ===================== */

  /// 解析 HARVEST 模式的窗口。
/// 
/// 使用当前 HARVEST 水位线(harvestWM)和可配置的回看时间以避免遗漏数据。 当水位线不存在时,视为首次运行,从用户提供的下界导出, 或通过使用默认窗口跨度从上界回滚。
/// 
/// 算法步骤:
/// 
/// @param cfg 窗口偏移配置
/// @param harvestWM HARVEST 水位线
/// @param userFrom 用户请求的下界
/// @param userTo 用户请求的上界
/// @param nowSafe 安全的当前时间(减去延迟后)
/// @param timezone 时区
/// @return 规划窗口
  private PlannerWindow resolveHarvest(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant harvestWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    // 步骤1: 解析窗口大小和回看时间
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
    Duration lookback =
        resolveDuration(
            cfg == null ? null : cfg.lookbackValue(),
            cfg == null ? null : cfg.lookbackUnitCode(),
            Duration.ZERO);

    log.debug(
        "解析 HARVEST 窗口: harvestWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}, lookback={}",
        harvestWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize,
        lookback);

    // 步骤2: 计算上界候选
    Instant toCandidate = minInstant(userTo, nowSafe);
    if (toCandidate == null) {
      toCandidate = nowSafe; // 没有用户上界 -> 使用 nowSafe
    }

    // 步骤3: 计算下界候选
    Instant fromCandidate;
    if (harvestWM != null) {
      // 有水位线: 从水位线回退 lookback 时间
      Instant lowerByCursor = harvestWM.minus(lookback);
      fromCandidate = maxInstant(lowerByCursor, userFrom);
      log.debug("使用 harvestWM: lowerByCursor={}, fromCandidate={}", lowerByCursor, fromCandidate);
    } else if (userFrom != null) {
      // 没有水位线但有用户from
      fromCandidate = userFrom;
      log.debug("无 harvestWM, 使用 userFrom: {}", fromCandidate);
    } else {
      // 默认: 从 toCandidate 回退一个窗口
      fromCandidate = toCandidate.minus(windowSize);
      log.debug("无 harvestWM 或 userFrom, 从 toCandidate 回退: {}", fromCandidate);
    }

    // 步骤4: 如果是日历模式,进行对齐
    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      Instant beforeAlign = fromCandidate;
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
      log.debug(
          "日历对齐已应用: from {} -> {}, to {} -> {}",
          beforeAlign,
          fromCandidate,
          toCandidate,
          toCandidate);
    }

    // 步骤5: 检查空窗口
    if (!toCandidate.isAfter(fromCandidate)) {
      log.debug("对齐后 HARVEST 窗口为空: {} >= {}", fromCandidate, toCandidate);
      return nullWindowIfEmpty(fromCandidate, toCandidate);
    }

    log.debug("已解析 HARVEST 窗口: [{}, {})", fromCandidate, toCandidate);
    return safeWindow(fromCandidate, toCandidate);
  }

  /* ===================== BACKFILL(回填模式) ===================== */

  /// 解析 BACKFILL 模式的窗口。
/// 
/// BACKFILL 水位线(backfillWM)控制最小下界。上界受用户提供的 'to' 和 nowSafe 限制。 forwardWM 保留供将来使用。
/// 
/// 算法步骤:
/// 
/// @param cfg 窗口偏移配置
/// @param backfillWM BACKFILL 水位线
/// @param forwardWM 前向水位线(保留,暂未使用)
/// @param userFrom 用户请求的下界
/// @param userTo 用户请求的上界
/// @param nowSafe 安全的当前时间
/// @param timezone 时区
/// @return 规划窗口
  private PlannerWindow resolveBackfill(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant backfillWM,
      Instant forwardWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    // 步骤1: 解析窗口大小
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);

    log.debug(
        "解析 BACKFILL 窗口: backfillWM={}, forwardWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}",
        backfillWM,
        forwardWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize);

    // 步骤2: 计算上锚点
    // forwardWM 作为上锚点候选(例如 HARVEST 水位线)。此解析器根据选定模式获取单个游标水位线。
    // 为避免现在扩展接口,假设提供的水位线对应 BACKFILL。如果将来需要 forwardWM,
    // 可通过扩展合约或丰富的触发器参数传递。
    Instant upperAnchor = minInstant(userTo, forwardWM, nowSafe);
    if (upperAnchor == null) {
      upperAnchor = nowSafe;
    }

    // 步骤3: 计算下界候选
    Instant fromCandidate;
    if (backfillWM != null) {
      fromCandidate = maxInstant(backfillWM, userFrom);
      log.debug("使用 backfillWM: fromCandidate={}", fromCandidate);
    } else if (userFrom != null) {
      fromCandidate = userFrom;
      log.debug("无 backfillWM, 使用 userFrom: {}", fromCandidate);
    } else {
      fromCandidate = upperAnchor.minus(windowSize);
      log.debug("无 backfillWM 或 userFrom, 从 upperAnchor 回退: {}", fromCandidate);
    }

    // 步骤4: 边界修正 - 防止跨越上界
    if (fromCandidate.isAfter(upperAnchor)) {
      log.debug("fromCandidate {} 超过 upperAnchor {}, 进行限制", fromCandidate, upperAnchor);
      fromCandidate = upperAnchor;
    }

    // 步骤5: 如果是日历模式,进行对齐
    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      upperAnchor = alignFloor(upperAnchor, cfg.calendarAlignTo(), zone);
      log.debug("日历对齐已应用: from={}, to={}", fromCandidate, upperAnchor);
    }

    // 步骤6: 检查空窗口
    if (!upperAnchor.isAfter(fromCandidate)) {
      log.debug("BACKFILL 窗口为空: {} >= {}", fromCandidate, upperAnchor);
      return nullWindowIfEmpty(fromCandidate, upperAnchor);
    }

    log.debug("已解析 BACKFILL 窗口: [{}, {})", fromCandidate, upperAnchor);
    return safeWindow(fromCandidate, upperAnchor);
  }

  /* ===================== UPDATE(更新模式) ===================== */

  /// 解析 UPDATE 模式的窗口。
/// 
/// 区分时间驱动和 ID 驱动流程;时间驱动需要用户窗口或 offsetType 为 DATE。
/// 
/// 算法步骤:
/// 
/// @param cfg 窗口偏移配置
/// @param updateWM UPDATE 水位线
/// @param userFrom 用户请求的下界
/// @param userTo 用户请求的上界
/// @param nowSafe 安全的当前时间
/// @param timezone 时区
/// @return 规划窗口
  private PlannerWindow resolveUpdate(
      ProvenanceConfigSnapshot.WindowOffsetConfig cfg,
      Instant updateWM,
      Instant userFrom,
      Instant userTo,
      Instant nowSafe,
      String timezone) {
    // 步骤1: 解析窗口大小并判断驱动类型
    Duration windowSize = resolveWindowSize(cfg, DEFAULT_WINDOW_SIZE);
    boolean timeDriven =
        (cfg != null
                && StrUtil.equalsIgnoreCase(cfg.offsetTypeCode(), "DATE")
                && (userFrom != null || userTo != null))
            || (userFrom != null || userTo != null);

    log.debug(
        "解析 UPDATE 窗口: updateWM={}, userFrom={}, userTo={}, nowSafe={}, windowSize={}, timeDriven={}",
        updateWM,
        userFrom,
        userTo,
        nowSafe,
        windowSize,
        timeDriven);

    // 步骤2: 根据驱动类型计算窗口
    Instant fromCandidate;
    Instant toCandidate;
    if (timeDriven) {
      // 时间驱动模式
      toCandidate = minInstant(userTo, nowSafe);
      if (toCandidate == null) {
        toCandidate = nowSafe;
      }
      if (updateWM != null && userFrom != null) {
        fromCandidate = maxInstant(updateWM, userFrom);
        log.debug("时间驱动,有 updateWM 和 userFrom: fromCandidate={}", fromCandidate);
      } else if (updateWM != null) {
        fromCandidate = updateWM;
        if (userFrom != null && userFrom.isAfter(fromCandidate)) {
          fromCandidate = userFrom;
        }
        log.debug("时间驱动,有 updateWM: fromCandidate={}", fromCandidate);
      } else if (userFrom != null) {
        fromCandidate = userFrom;
        log.debug("时间驱动,仅有 userFrom: fromCandidate={}", fromCandidate);
      } else {
        fromCandidate = nowSafe.minus(windowSize);
        log.debug("时间驱动默认: fromCandidate={}", fromCandidate);
      }
    } else { // ID驱动模式
      if (userFrom != null || userTo != null) {
        toCandidate = minInstant(userTo, nowSafe);
        if (toCandidate == null) {
          toCandidate = nowSafe;
        }
        fromCandidate = userFrom != null ? userFrom : toCandidate.minus(windowSize);
        log.debug("ID驱动,有用户边界: fromCandidate={}", fromCandidate);
      } else {
        toCandidate = nowSafe;
        fromCandidate = nowSafe.minus(windowSize);
        log.debug("ID驱动默认: fromCandidate={}", fromCandidate);
      }
    }

    // 步骤3: 如果是日历模式,进行对齐
    if (isCalendarMode(cfg) && cfg != null) {
      ZoneId zone = resolveZone(timezone);
      fromCandidate = alignFloor(fromCandidate, cfg.calendarAlignTo(), zone);
      toCandidate = alignFloor(toCandidate, cfg.calendarAlignTo(), zone);
      log.debug("日历对齐已应用: from={}, to={}", fromCandidate, toCandidate);
    }

    // 步骤4: 检查空窗口
    if (!toCandidate.isAfter(fromCandidate)) {
      log.debug("UPDATE 窗口为空: {} >= {}", fromCandidate, toCandidate);
      return nullWindowIfEmpty(fromCandidate, toCandidate);
    }

    log.debug("已解析 UPDATE 窗口: [{}, {})", fromCandidate, toCandidate);
    return safeWindow(fromCandidate, toCandidate);
  }

  /* ===================== 辅助方法 ===================== */

  /// 构造安全窗口: 如果长度低于最小阈值,扩展 'to' 以避免零长度窗口。
/// 
/// @param from 窗口起始时间
/// @param to 窗口结束时间
/// @return 安全窗口(至少 MIN_EFFECTIVE_WINDOW 长度)
  private PlannerWindow safeWindow(Instant from, Instant to) {
    if (Duration.between(from, to).compareTo(MIN_EFFECTIVE_WINDOW) < 0) {
      to = from.plus(MIN_EFFECTIVE_WINDOW);
    }
    return new PlannerWindow(from, to);
  }

  /// 最小窗口回退: 当对齐导致 from >= to 时,返回 [from, from + MIN_EFFECTIVE_WINDOW], 以便调用方保持线性流程,同时仍能检测原始空状态。
/// 
/// @param from 窗口起始时间
/// @param to 窗口结束时间(可能等于或小于 from)
/// @return 最小有效窗口
  private PlannerWindow nullWindowIfEmpty(Instant from, Instant to) {
    // 返回最小窗口以便上游可以继续,验证器可以处理空情况;
    // 避免在规划期间抛出 IllegalArgumentException
    return new PlannerWindow(from, from.plus(MIN_EFFECTIVE_WINDOW));
  }
}
