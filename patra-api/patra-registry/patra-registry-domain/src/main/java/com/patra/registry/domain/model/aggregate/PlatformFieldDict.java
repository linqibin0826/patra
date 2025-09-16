package com.patra.registry.domain.model.aggregate;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.registry.domain.model.enums.Cardinality;
import com.patra.registry.domain.model.enums.DataType;
import com.patra.common.enums.IngestDateType;
import com.patra.registry.domain.model.vo.RecordRemark;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台字段字典聚合根。
 *
 * <p>职责：统一平台字段语义与类型信息，作为“平台标准字段”的权威来源；保护字段键/类型/日期标识等不变量。
 * <p>行为：改名（字段显示名不在此，字段键不允许更改）、调整数据类型/基数、标记是否日期与映射 datetype、登记备注。
 * <p>一致性边界：外部只能通过本聚合修改字段定义，避免直接篡改导致的数据不一致。
 *
 * <p>事件：使用同样的“拉取”模式，供应用层发布。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@ToString
@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
public class PlatformFieldDict {

    /**
     * 聚合根ID（技术键）
     */
    private Long id;

    /**
     * 平台统一字段键（业务键，如 pub_date/title_abstract）；不可变更
     */
    private String fieldKey;

    /**
     * 数据类型
     */
    private DataType dataType;

    /**
     * 基数：单值/多值
     */
    private Cardinality cardinality;

    /**
     * 是否日期字段（DateLens 判定用）
     */
    private Boolean isDate;

    /**
     * 仅日期类使用的 datetype 映射
     */
    private IngestDateType datetype;

    /**
     * 记录备注
     */
    @Builder.Default
    private List<RecordRemark> recordRemarks = new ArrayList<>();

    /**
     * 乐观锁版本号
     */
    private Long version;

    /**
     * 领域事件（拉取后清空）
     */
    @Builder.Default
    private transient List<Object> domainEvents = new ArrayList<>();

    // ------------------------- 工厂与不变式 -------------------------

    /**
     * 创建字段定义
     */
    public static PlatformFieldDict create(Long id,
                                           String fieldKey,
                                           DataType dataType,
                                           Cardinality cardinality,
                                           Boolean isDate,
                                           IngestDateType datetype,
                                           String operator) {
        PlatformFieldDict agg = PlatformFieldDict.builder()
                .id(id)
                .fieldKey(StrUtil.trim(fieldKey))
                .dataType(dataType)
                .cardinality(cardinality)
                .isDate(Boolean.TRUE.equals(isDate))
                .datetype(datetype)
                .build();
        agg.validateInvariants();
        agg.appendRemark("created", operator);
        // 领域事件可根据需要扩展为具体类型；此处先避免额外样板
        return agg;
    }

    /**
     * 聚合级不变式校验
     */
    public void validateInvariants() {
        Assert.isFalse(StrUtil.isBlank(fieldKey), "fieldKey blank");
        Assert.notNull(dataType, "dataType is null");
        Assert.notNull(cardinality, "cardinality is null");
        // 日期字段时 datetype 必填；非日期时必须为 null
        if (Boolean.TRUE.equals(isDate)) {
            Assert.notNull(datetype, "datetype required when isDate=true");
        } else {
            Assert.isTrue(datetype == null, "datetype must be null when isDate=false");
        }
    }

    // ------------------------- 行为 -------------------------

    /**
     * 调整数据类型（幂等）
     */
    public void changeDataType(DataType newType, String operator) {
        Assert.notNull(newType, "newType is null");
        if (newType == this.dataType) return;
        this.dataType = newType;
        appendRemark("dataType->" + newType.name(), operator);
        registerUpdated();
    }

    /**
     * 调整基数（幂等）
     */
    public void changeCardinality(Cardinality newCardinality, String operator) {
        Assert.notNull(newCardinality, "newCardinality is null");
        if (newCardinality == this.cardinality) return;
        this.cardinality = newCardinality;
        appendRemark("cardinality->" + newCardinality, operator);
        registerUpdated();
    }

    /**
     * 标记为日期字段（设置 datetype）
     */
    public void markAsDate(IngestDateType newDatetype, String operator) {
        Assert.notNull(newDatetype, "newDatetype is null");
        this.isDate = true;
        this.datetype = newDatetype;
        validateInvariants();
        appendRemark("isDate=true,datetype->" + newDatetype, operator);
        registerUpdated();
    }

    /**
     * 取消日期标记（datetype 置空）
     */
    public void unmarkDate(String operator) {
        if (!Boolean.TRUE.equals(this.isDate) && this.datetype == null) return;
        this.isDate = false;
        this.datetype = null;
        validateInvariants();
        appendRemark("isDate=false", operator);
        registerUpdated();
    }

    /**
     * 追加备注
     */
    public void appendRemark(String note, String by) {
        String n = StrUtil.nullToDefault(StrUtil.trim(note), "");
        String who = StrUtil.nullToDefault(StrUtil.trim(by), "system");
        this.recordRemarks.add(RecordRemark.builder()
                .time(LocalDateTime.now())
                .by(who)
                .note(n)
                .build());
    }

    /**
     * 拉取并清空事件
     */
    public List<Object> pullDomainEvents() {
        if (CollUtil.isEmpty(domainEvents)) return List.of();
        List<Object> copy = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return copy;
    }

    private void registerUpdated() {
        // 可替换为 PlatformFieldDictEvents.* 事件类型；按需要扩展
        this.domainEvents.add("PlatformFieldDictUpdated:" + this.fieldKey + "@" + LocalDateTime.now());
    }
}
