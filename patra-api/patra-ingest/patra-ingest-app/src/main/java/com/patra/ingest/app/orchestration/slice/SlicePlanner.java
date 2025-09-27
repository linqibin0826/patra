package com.patra.ingest.app.orchestration.slice;

import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import java.util.List;

/**
 * 切片策略接口。
 * <p>定义不同切片策略的通用能力，包括策略编码以及根据上下文拆分窗口的逻辑。</p>
 *
 * <p>策略实现需满足：
 * <ul>
 *   <li>code 唯一，用于在注册表中定位实现类；</li>
 *   <li>slice 返回的切片集合必须按顺序排列，sequence 从 1 开始递增；</li>
 *   <li>遇到无法切片的场景返回空集合，并由上层处理。</li>
 * </ul></p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface SlicePlanner {

    /**
     * 返回策略编码，通常对应配置中的 strategy 标识。
     *
     * @return 策略编码
     */
    String code();

    /**
     * 根据上下文拆分计划窗口，生成切片列表。
     *
     * @param context 切片策略上下文，包含窗口、表达式与配置快照
     * @return 切片集合，按顺序排列；无法切片时返回空集合
     */
    List<SlicePlan> slice(SlicePlanningContext context);
}
