package com.patra.ingest.domain.model.vo;

import lombok.Value;
import java.time.LocalDateTime;

/**
 * 时间窗口值对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class TimeWindow {
    
    LocalDateTime from;
    LocalDateTime to;
    
    public TimeWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("时间窗口的起止时间不能为空");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("时间窗口起始时间必须早于结束时间");
        }
        this.from = from;
        this.to = to;
    }
    
    /**
     * 判断时间窗口是否包含指定时间点
     */
    public boolean contains(LocalDateTime dateTime) {
        return !dateTime.isBefore(from) && dateTime.isBefore(to);
    }
    
    /**
     * 判断两个时间窗口是否重叠
     */
    public boolean overlaps(TimeWindow other) {
        return from.isBefore(other.to) && other.from.isBefore(to);
    }
    
    /**
     * 计算两个时间窗口的交集
     */
    public TimeWindow intersect(TimeWindow other) {
        LocalDateTime maxFrom = from.isAfter(other.from) ? from : other.from;
        LocalDateTime minTo = to.isBefore(other.to) ? to : other.to;
        
        if (!maxFrom.isBefore(minTo)) {
            throw new IllegalArgumentException("时间窗口没有交集");
        }
        
        return new TimeWindow(maxFrom, minTo);
    }
}
