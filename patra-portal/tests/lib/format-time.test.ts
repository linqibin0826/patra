import { describe, expect, it } from "vitest";
import {
  addDays,
  dayGroupOf,
  formatClock,
  formatDate,
  formatDateMinute,
  formatDateTime,
  formatGroupedTime,
  formatMonthDay,
  formatRelative,
} from "@/lib/format-time";

/// 北京时间 2026-10-23 16:00
const NOW = Date.parse("2026-10-23T08:00:00Z");
const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const iso = (ms: number) => new Date(ms).toISOString();

describe("formatDate", () => {
  it("按北京时间写到日", () => {
    expect(formatDate("2026-11-19T08:00:00Z")).toBe("2026 年 11 月 19 日");
  });

  it("UTC 还是前一天、北京时间已是次日时取北京时间", () => {
    expect(formatDate("2026-11-18T16:30:00Z")).toBe("2026 年 11 月 19 日");
  });
});

describe("formatDateTime / formatDateMinute", () => {
  it("日期时间到分钟", () => {
    expect(formatDateTime("2026-10-20T01:12:00Z")).toBe("2026-10-20 09:12");
  });

  it("中文日期加时刻", () => {
    expect(formatDateMinute("2026-11-06T00:00:00Z")).toBe("2026 年 11 月 6 日 08:00");
  });
});

describe("formatClock", () => {
  it("同一天只写时刻", () => {
    expect(formatClock("2026-10-23T07:42:00Z", NOW)).toBe("15:42");
  });

  it("不是同一天写月日与时刻", () => {
    expect(formatClock("2026-10-23T16:30:00Z", NOW)).toBe("10 月 24 日 00:30");
  });
});

describe("formatRelative", () => {
  it.each([
    [iso(NOW - 30_000), "刚刚"],
    [iso(NOW + 5 * MINUTE), "刚刚"],
    [iso(NOW - 5 * MINUTE), "5 分钟前"],
    [iso(NOW - 2 * HOUR), "2 小时前"],
    ["2026-10-22T17:00:00Z", "15 小时前"],
    ["2026-10-22T15:30:00Z", "昨天 23:30"],
    ["2026-10-22T13:40:00Z", "昨天 21:40"],
    [iso(NOW - 3 * DAY), "3 天前"],
    [iso(NOW - 7 * DAY), "7 天前"],
    [iso(NOW - 8 * DAY), "10 月 15 日"],
    ["2025-12-30T04:00:00Z", "2025 年 12 月 30 日"],
  ])("%s → %s", (value, expected) => {
    expect(formatRelative(value, NOW)).toBe(expected);
  });
});

describe("addDays", () => {
  it("返回加上天数后的 ISO 时间", () => {
    expect(addDays(NOW, 15)).toBe("2026-11-07T08:00:00.000Z");
  });
});

describe("formatMonthDay", () => {
  it("北京时间的月日", () => {
    expect(formatMonthDay("2026-10-23T17:00:00Z")).toBe("10 月 24 日");
  });
});

describe("formatMonthDayTime", () => {
  it("北京时间的月日时分", async () => {
    const { formatMonthDayTime } = await import("@/lib/format-time");
    expect(formatMonthDayTime("2026-10-26T10:40:00Z")).toBe("10 月 26 日 18:40");
  });
});

/// 北京时间 2026-10-29 00:00:30，刚过零点
const JUST_AFTER_MIDNIGHT = Date.parse("2026-10-28T16:00:30Z");

describe("dayGroupOf", () => {
  it("按北京时间的自然日分：零点之后是今天，零点之前一秒是昨天", () => {
    expect(dayGroupOf("2026-10-28T16:00:00Z", JUST_AFTER_MIDNIGHT)).toBe("today");
    expect(dayGroupOf("2026-10-28T15:59:59Z", JUST_AFTER_MIDNIGHT)).toBe("yesterday");
    expect(dayGroupOf("2026-10-27T15:59:59Z", JUST_AFTER_MIDNIGHT)).toBe("earlier");
  });

  it("时钟偏差造成的未来时间算今天", () => {
    expect(dayGroupOf("2026-10-28T16:00:35Z", JUST_AFTER_MIDNIGHT)).toBe("today");
  });
});

describe("formatGroupedTime", () => {
  it("今天写相对时间", () => {
    expect(formatGroupedTime("2026-10-28T16:00:00Z", JUST_AFTER_MIDNIGHT)).toBe("刚刚");
    expect(formatGroupedTime(iso(NOW - 3 * HOUR), NOW)).toBe("3 小时前");
  });

  it("昨天只写时分", () => {
    expect(formatGroupedTime("2026-10-28T13:40:00Z", JUST_AFTER_MIDNIGHT)).toBe("21:40");
  });

  it("更早写月日，往年带年份", () => {
    expect(formatGroupedTime("2026-10-21T02:00:00Z", JUST_AFTER_MIDNIGHT)).toBe("10 月 21 日");
    expect(formatGroupedTime("2025-10-21T02:00:00Z", JUST_AFTER_MIDNIGHT)).toBe(
      "2025 年 10 月 21 日",
    );
  });
});
