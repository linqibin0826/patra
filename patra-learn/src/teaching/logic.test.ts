import { describe, expect, it } from "vitest";
import {
  formatControlValue,
  interpolate,
  matchCase,
  type StepperState,
  segmentInline,
  stepperNext,
  stepperPrev,
} from "./logic";

describe("行内代码", () => {
  it("反引号之间的是代码，其余原样", () => {
    expect(segmentInline("发送时带 `mandatory` 才会退回")).toEqual([
      { code: false, text: "发送时带 " },
      { code: true, text: "mandatory" },
      { code: false, text: " 才会退回" },
    ]);
  });

  it("没配对的反引号当普通字符", () => {
    expect(segmentInline("a`b")).toEqual([{ code: false, text: "a`b" }]);
  });
});

describe("参数组件", () => {
  const controls = [
    { type: "switch" as const, id: "bound", label: "有队列", default: false },
    {
      type: "slider" as const,
      id: "interval",
      label: "间隔",
      min: 10,
      max: 300,
      step: 10,
      default: 60,
      unit: "秒",
    },
  ];
  const cases = [
    { when: { bound: true }, verdict: { tone: "ok" as const, text: "正常" } },
    {
      when: { interval: [10, 50] as [number, number] },
      verdict: { tone: "bad" as const, text: "太勤" },
    },
    { verdict: { tone: "ok" as const, text: "兜底" } },
  ];

  it("第一个匹配的 case 生效，滑块按闭区间匹配", () => {
    expect(matchCase(cases, { bound: true, interval: 20 })?.verdict.text).toBe("正常");
    expect(matchCase(cases, { bound: false, interval: 50 })?.verdict.text).toBe("太勤");
    expect(matchCase(cases, { bound: false, interval: 60 })?.verdict.text).toBe("兜底");
  });

  it("没有任何 case 匹配时返回 undefined", () => {
    expect(matchCase([cases[0] as (typeof cases)[number]], { bound: false })).toBeUndefined();
  });

  it("文字里的 {id} 换成当前值加单位", () => {
    expect(interpolate("每 {interval} 补投，{nope} 不动", controls, { interval: 60 })).toBe(
      "每 60 秒 补投，{nope} 不动",
    );
    expect(formatControlValue(controls[1], 120)).toBe("120 秒");
  });
});

describe("逐步动画的推进", () => {
  const guesses = [{ before: 3 }, { before: 5 }];
  const start: StepperState = { step: 0, gate: null, picked: null, passed: [] };

  it("没有你猜的步骤直接往前走", () => {
    expect(stepperNext(start, 5, guesses)).toEqual({ ...start, step: 1 });
  });

  it("走到第 3 步之前先停在你猜上，不前进", () => {
    const s = stepperNext({ ...start, step: 1 }, 5, guesses);
    expect(s).toEqual({ step: 1, gate: 0, picked: null, passed: [] });
  });

  it("答完你猜再点才前进，答过的不再拦", () => {
    const answered: StepperState = { step: 1, gate: 0, picked: 1, passed: [] };
    const s = stepperNext(answered, 5, guesses);
    expect(s).toEqual({ step: 2, gate: null, picked: null, passed: [0] });
    expect(stepperNext(stepperPrev(s), 5, guesses)).toEqual(s);
  });

  it("你猜还没答时不能前进", () => {
    const waiting: StepperState = { step: 1, gate: 0, picked: null, passed: [] };
    expect(stepperNext(waiting, 5, guesses)).toBe(waiting);
  });

  it("上一步：在你猜上先关掉你猜，否则退一步", () => {
    expect(stepperPrev({ step: 1, gate: 0, picked: null, passed: [] })).toEqual({
      step: 1,
      gate: null,
      picked: null,
      passed: [],
    });
    expect(stepperPrev({ ...start, step: 2 })).toEqual({ ...start, step: 1 });
    expect(stepperPrev(start)).toBe(start);
  });

  it("最后一步再点从头开始", () => {
    expect(stepperNext({ step: 4, gate: null, picked: null, passed: [0, 1] }, 5, guesses)).toEqual(
      start,
    );
  });
});
