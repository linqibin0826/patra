package com.patra.ingest.app.orchestration.window.support;

import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static com.patra.ingest.app.orchestration.window.support.PlanningWindowSupport.*;

class PlanningWindowSupportTest {

    @Test
    void computeLaggedNowShouldApplyConfigLag() {
        // 验证滞后秒配置可正确减少安全时间
        Instant now = Instant.parse("2024-01-01T12:00:00Z");
        ProvenanceConfigSnapshot.WindowOffsetConfig config = offsetConfigBuilder()
                .windowModeCode("SLIDING")
                .windowSizeValue(6)
                .windowSizeUnitCode("HOUR")
                .watermarkLagSeconds(3_600)
                .build();
        Instant actual = computeLaggedNow(now, config, Duration.ZERO);
        Assertions.assertEquals(Instant.parse("2024-01-01T11:00:00Z"), actual);
    }

    @Test
    void resolveWindowSizeShouldFallbackToDefault() {
        // 未配置窗口长度时应退回默认值
        Duration defaultSize = Duration.ofHours(24);
        Duration resolved = resolveWindowSize(null, defaultSize);
        Assertions.assertEquals(defaultSize, resolved);

        ProvenanceConfigSnapshot.WindowOffsetConfig config = offsetConfigBuilder()
                .windowSizeValue(2)
                .windowSizeUnitCode("DAY")
                .build();
        Assertions.assertEquals(Duration.ofDays(2), resolveWindowSize(config, defaultSize));
    }

    @Test
    void alignFloorShouldRespectCalendarUnit() {
        // 日对齐场景需按时区截断
        ZoneId zone = resolveZone("Asia/Shanghai");
        Instant instant = Instant.parse("2024-03-15T10:45:12Z");
        Instant aligned = alignFloor(instant, "DAY", zone);
        Assertions.assertEquals(Instant.parse("2024-03-14T16:00:00Z"), aligned);
    }

    private WindowOffsetConfigBuilder offsetConfigBuilder() {
        return new WindowOffsetConfigBuilder();
    }

    private static final class WindowOffsetConfigBuilder {
        private String windowModeCode;
        private Integer windowSizeValue;
        private String windowSizeUnitCode;
        private String calendarAlignTo;
        private Integer lookbackValue;
        private String lookbackUnitCode;
        private Integer watermarkLagSeconds;

        WindowOffsetConfigBuilder windowModeCode(String value) {
            this.windowModeCode = value;
            return this;
        }

        WindowOffsetConfigBuilder windowSizeValue(Integer value) {
            this.windowSizeValue = value;
            return this;
        }

        WindowOffsetConfigBuilder windowSizeUnitCode(String value) {
            this.windowSizeUnitCode = value;
            return this;
        }

        WindowOffsetConfigBuilder calendarAlignTo(String value) {
            this.calendarAlignTo = value;
            return this;
        }

        WindowOffsetConfigBuilder lookbackValue(Integer value) {
            this.lookbackValue = value;
            return this;
        }

        WindowOffsetConfigBuilder lookbackUnitCode(String value) {
            this.lookbackUnitCode = value;
            return this;
        }

        WindowOffsetConfigBuilder watermarkLagSeconds(Integer value) {
            this.watermarkLagSeconds = value;
            return this;
        }

        ProvenanceConfigSnapshot.WindowOffsetConfig build() {
            return new ProvenanceConfigSnapshot.WindowOffsetConfig(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    windowModeCode,
                    windowSizeValue,
                    windowSizeUnitCode,
                    calendarAlignTo,
                    lookbackValue,
                    lookbackUnitCode,
                    null,
                    null,
                    watermarkLagSeconds,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
