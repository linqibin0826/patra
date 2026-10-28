import { describe, expect, it } from "vitest";
import { constraintsFor, daysBetween, MIN_DATE, todayInShanghai } from "@/lib/experience/rules";

describe("constraintsFor：首轮结果与最终结果的联动（spec 6.3）", () => {
  it("直接拒稿：最终只能拒稿，不填最终日期、审稿轮数、审稿质量", () => {
    expect(constraintsFor("DESK_REJECT", "")).toEqual({
      lockedFinalOutcome: "REJECTED",
      finalDecisionOn: "terminal",
      reviewRounds: "none",
      reviewQuality: "none",
    });
  });

  it("审后拒稿：最终只能拒稿，审稿轮数固定为 1", () => {
    expect(constraintsFor("REJECT_AFTER_REVIEW", "REJECTED")).toEqual({
      lockedFinalOutcome: "REJECTED",
      finalDecisionOn: "terminal",
      reviewRounds: "one",
      reviewQuality: "enabled",
    });
  });

  it("首轮录用：最终只能录用，审稿轮数固定为 1", () => {
    expect(constraintsFor("ACCEPT", "")).toEqual({
      lockedFinalOutcome: "ACCEPTED",
      finalDecisionOn: "terminal",
      reviewRounds: "one",
      reviewQuality: "enabled",
    });
  });

  it("大修且进行中：不填最终决定日期", () => {
    expect(constraintsFor("MAJOR_REVISION", "IN_PROGRESS")).toEqual({
      lockedFinalOutcome: null,
      finalDecisionOn: "in-progress",
      reviewRounds: "enabled",
      reviewQuality: "enabled",
    });
  });

  it("小修且录用：都能填", () => {
    expect(constraintsFor("MINOR_REVISION", "ACCEPTED")).toEqual({
      lockedFinalOutcome: null,
      finalDecisionOn: "enabled",
      reviewRounds: "enabled",
      reviewQuality: "enabled",
    });
  });

  it("首轮还没选：都能填", () => {
    expect(constraintsFor("", "")).toEqual({
      lockedFinalOutcome: null,
      finalDecisionOn: "enabled",
      reviewRounds: "enabled",
      reviewQuality: "enabled",
    });
  });
});

describe("todayInShanghai", () => {
  it("按北京时间：UTC 10 月 27 日 16:30 已是北京 10 月 28 日", () => {
    expect(todayInShanghai(Date.UTC(2026, 9, 27, 16, 30))).toBe("2026-10-28");
  });

  it("UTC 10 月 27 日 15:59 还是北京 10 月 27 日", () => {
    expect(todayInShanghai(Date.UTC(2026, 9, 27, 15, 59))).toBe("2026-10-27");
  });

  it("日期下限是 1990-01-01（后端 TOO_EARLY）", () => {
    expect(MIN_DATE).toBe("1990-01-01");
  });
});

describe("daysBetween：表单上随填随算的用时", () => {
  it("按日期字符串算天数，不受时区与夏令时影响", () => {
    expect(daysBetween("2026-03-02", "2026-04-13")).toBe(42);
    expect(daysBetween("2026-03-02", "2026-06-01")).toBe(91);
    expect(daysBetween("2026-03-02", "2026-03-02")).toBe(0);
  });
});
