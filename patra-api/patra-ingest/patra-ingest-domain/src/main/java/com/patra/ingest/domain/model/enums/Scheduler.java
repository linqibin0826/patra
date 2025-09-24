package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 调度器来源枚举（DICT：ing_scheduler）。
 *
 * <p>持久化与脚本约定</p>
 * <ul>
 *   <li>数据库字段名：<b>scheduler_code</b></li>
 *   <li>出现位置：ing_schedule_instance.scheduler_code（参见脚本 V0.1.0__init_ingest_schema.sql）</li>
 *   <li>字段定义：VARCHAR(32) NOT NULL DEFAULT 'XXL'，注释「DICT CODE(type=ing_scheduler)：调度器来源」</li>
 *   <li>存储值：使用本枚举的 {@code code} 属性（见 {@link #getCode()}）</li>
 * </ul>
 *
 * <p>取值与语义（与脚本/字典保持同步）</p>
 * <ul>
 *   <li>XXL —— XXL-Job 调度器（外部分布式调度中心）</li>
 *   <li>SPRING —— Spring 应用内定时任务（@Scheduled 等）</li>
 *   <li>QUARTZ —— Quartz 调度器（应用内/集群）</li>
 * </ul>
 *
 * <p>转换约定（纯领域层，无框架依赖）</p>
 * <ul>
 *   <li>输出编码：使用 {@link #getCode()} 获得 scheduler_code 标准字符串值（如 "XXL"）</li>
 *   <li>解析编码：使用 {@link #fromCode(String)}，忽略大小写与首尾空白；未知值抛出 IllegalArgumentException</li>
 * </ul>
 *
 * <p>演进约束</p>
 * <ul>
 *   <li>新增枚举值时，需同步更新：字典类型 <b>ing_scheduler</b>、默认值策略、以及相关校验与文档</li>
 *   <li>如需调整默认值，请同步修改建表/迁移脚本及数据初始化</li>
 * </ul>
 *
 * <p>分层与六边形架构位置：domain（实体/值对象/枚举），不依赖任何框架</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum Scheduler {

    /**
     * XXL-Job 调度器（外部分布式调度中心）
     */
    XXL("XXL", "XXL-Job调度器"),
    /**
     * Spring 应用内定时任务
     */
    SPRING("SPRING", "Spring定时任务"),
    /**
     * Quartz 调度器（应用内/集群）
     */
    QUARTZ("QUARTZ", "Quartz调度器");

    /**
     * 字典编码（持久化至 scheduler_code / 对外编码）。
     */
    private final String code;
    /**
     * 人类可读描述（用于展示/文档）。
     */
    private final String description;

    Scheduler(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据字典编码解析为枚举：忽略大小写与首尾空白。
     *
     * @param value 字符串编码（如 "XXL"/"spring"/" Quartz ")
     * @return 对应的 {@link Scheduler}
     * @throws IllegalArgumentException 当 {@code value} 为空或不识别时抛出
     */
    public static Scheduler fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Scheduler code cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        for (Scheduler type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown scheduler code: " + value);
    }
}
