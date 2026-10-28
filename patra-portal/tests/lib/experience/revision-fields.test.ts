import { makeRevision } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import { fieldChanges, fieldValues } from "@/lib/experience/revision-fields";

describe("fieldChanges：两版之间结构化字段的改动（spec 8.3）", () => {
  it("改了首轮结果", () => {
    expect(fieldChanges(makeRevision(), makeRevision({ firstDecision: "MINOR_REVISION" }))).toEqual(
      [{ label: "首轮结果", from: "大修", to: "小修" }],
    );
  });

  it("从未填到付了", () => {
    expect(fieldChanges(makeRevision({ apcPaid: null }), makeRevision({ apcPaid: true }))).toEqual([
      { label: "APC", from: "未填", to: "付了" },
    ]);
  });

  it("日期与评分的写法", () => {
    expect(
      fieldChanges(
        makeRevision({ submittedOn: "2026-03-01", reviewQuality: 3 }),
        makeRevision({ submittedOn: "2026-03-02", reviewQuality: 4 }),
      ),
    ).toEqual([
      { label: "投稿日期", from: "2026 年 3 月 1 日", to: "2026 年 3 月 2 日" },
      { label: "审稿质量", from: "3 / 5", to: "4 / 5" },
    ]);
  });

  it("没变的不出现；正文不在这里比", () => {
    expect(fieldChanges(makeRevision(), makeRevision({ body: "别的" }))).toEqual([]);
  });
});

describe("fieldValues：最初版本只列值", () => {
  it("11 个字段，没填写未填", () => {
    const values = fieldValues(makeRevision({ finalDecisionOn: null }));
    expect(values).toHaveLength(11);
    expect(values[0]).toEqual({ label: "投稿日期", value: "2026 年 3 月 2 日" });
    expect(values.find((item) => item.label === "最终决定日期")?.value).toBe("未填");
  });
});
