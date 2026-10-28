import { makeExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import {
  formatDateCn,
  formatDays,
  formatDetailLine,
  formatOutcomeLine,
  formatPercent,
  formatPublished,
  formatRange,
  formatSubmittedMonth,
  formatWeeksHint,
  summaryScopeLine,
} from "@/lib/experience/format";

describe("时长（简报 §3.22）", () => {
  it("以天为主单位", () => {
    expect(formatDays(42)).toBe("42 天");
    expect(formatDays(0)).toBe("0 天");
  });

  it("满 28 天才补约几周，四舍五入到整周", () => {
    expect(formatWeeksHint(27)).toBeNull();
    expect(formatWeeksHint(28)).toBe("约 4 周");
    expect(formatWeeksHint(42)).toBe("约 6 周");
    expect(formatWeeksHint(45)).toBe("约 6 周");
    expect(formatWeeksHint(46)).toBe("约 7 周");
  });

  it("P25–P75 用天", () => {
    expect(formatRange(28, 63)).toBe("28–63 天");
  });
});

describe("日期", () => {
  it("经验页与表单回显写到日", () => {
    expect(formatDateCn("2026-03-02")).toBe("2026 年 3 月 2 日");
    expect(formatDateCn("2025-12-31")).toBe("2025 年 12 月 31 日");
  });

  it("列表与主页写投稿月份", () => {
    expect(formatSubmittedMonth("2026-03-02")).toBe("2026 年 3 月投稿");
  });
});

describe("结果行（简报 §3.14）", () => {
  it("修改类：首轮 → 最终", () => {
    expect(
      formatOutcomeLine({
        firstRoundDays: 42,
        firstDecision: "MAJOR_REVISION",
        finalOutcome: "ACCEPTED",
      }),
    ).toBe("首轮 42 天 · 大修  →  录用");
  });

  it("最终结果进行中", () => {
    expect(
      formatOutcomeLine({
        firstRoundDays: 30,
        firstDecision: "MINOR_REVISION",
        finalOutcome: "IN_PROGRESS",
      }),
    ).toBe("首轮 30 天 · 小修  →  进行中");
  });

  it("撤稿", () => {
    expect(
      formatOutcomeLine({
        firstRoundDays: 50,
        firstDecision: "MAJOR_REVISION",
        finalOutcome: "WITHDRAWN",
      }),
    ).toBe("首轮 50 天 · 大修  →  撤稿");
  });

  it.each([
    ["DESK_REJECT", "REJECTED", "首轮 9 天 · 直接拒稿"],
    ["REJECT_AFTER_REVIEW", "REJECTED", "首轮 9 天 · 审后拒稿"],
    ["ACCEPT", "ACCEPTED", "首轮 9 天 · 录用"],
  ] as const)("首轮就是最终（%s）不写箭头", (firstDecision, finalOutcome, line) => {
    expect(formatOutcomeLine({ firstRoundDays: 9, firstDecision, finalOutcome })).toBe(line);
  });
});

describe("统计数字", () => {
  it("百分比取整", () => {
    expect(formatPercent(0.5)).toBe("50%");
    expect(formatPercent(0.4666)).toBe("47%");
    expect(formatPercent(1)).toBe("100%");
  });

  it("口径行按窗口", () => {
    expect(summaryScopeLine(15, "3y")).toBe(
      "15 条经验 · 近 3 年（按投稿日期）· 首轮用时不含直接拒稿",
    );
    expect(summaryScopeLine(20, "all")).toBe("20 条经验 · 全部 · 首轮用时不含直接拒稿");
  });
});

describe("列表行的第三行与发布日期（简报 §3.14）", () => {
  it("填全时按顺序列出，没填的不出现", () => {
    expect(formatDetailLine(makeExperience())).toBe(
      "投稿到录用 91 天 · 审稿 2 轮 · 审稿质量 4/5 · 编辑沟通 3/5 · 付了 APC · 未转投",
    );
  });

  it("只有必填时为空串", () => {
    expect(
      formatDetailLine(
        makeExperience({
          finalDecisionOn: null,
          totalDays: null,
          reviewRounds: null,
          reviewQuality: null,
          editorCommunication: null,
          apcPaid: null,
          previouslyRejected: null,
        }),
      ),
    ).toBe("");
  });

  it("首轮即最终时不写投稿到最终的用时；转投、没付 APC", () => {
    expect(
      formatDetailLine(
        makeExperience({
          firstDecision: "DESK_REJECT",
          finalOutcome: "REJECTED",
          firstRoundDays: 9,
          totalDays: 9,
          reviewRounds: null,
          reviewQuality: null,
          editorCommunication: null,
          apcPaid: false,
          previouslyRejected: true,
        }),
      ),
    ).toBe("没付 APC · 转投过");
  });

  it("发布日期：今年写月日，往年写年月日（北京时间）", () => {
    const now = Date.parse("2026-10-28T02:00:00Z");
    expect(formatPublished("2026-06-03T02:00:00Z", now)).toBe("6 月 3 日");
    // UTC 去年 12 月 31 日 17:00 是北京今年 1 月 1 日
    expect(formatPublished("2025-12-31T17:00:00Z", now)).toBe("1 月 1 日");
    expect(formatPublished("2025-03-02T02:00:00Z", now)).toBe("2025 年 3 月 2 日");
  });
});
