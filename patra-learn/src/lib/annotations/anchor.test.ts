import { describe, expect, it } from "vitest";
import { describeQuote, locateQuote } from "./anchor";

const TEXT = "发布事件时，为每个监听器记一行。处理成功才算完成。失败的留着，为每个监听器补投。";

describe("describeQuote：选中的一段 → 原文与前后文", () => {
  it("记下选中的原文，前后各带最多 32 个字符的上下文", () => {
    const start = TEXT.indexOf("处理成功");
    const quote = describeQuote(TEXT, start, start + "处理成功才算完成".length);
    expect(quote).toEqual({
      exact: "处理成功才算完成",
      prefix: "发布事件时，为每个监听器记一行。",
      suffix: "。失败的留着，为每个监听器补投。",
    });
  });

  it("上下文超过 32 个字符时只截靠近选区的那一段", () => {
    const long = `${"甲".repeat(40)}目标${"乙".repeat(40)}`;
    const quote = describeQuote(long, 40, 42);
    expect(quote.prefix).toBe("甲".repeat(32));
    expect(quote.suffix).toBe("乙".repeat(32));
  });
});

describe("locateQuote：原文与前后文 → 当前文本里的位置", () => {
  it("原文只出现一次时直接定位", () => {
    const start = TEXT.indexOf("处理成功");
    expect(locateQuote(TEXT, describeQuote(TEXT, start, start + 8))).toEqual({
      start,
      end: start + 8,
    });
  });

  it("原文出现多次时，按前后文选最像的那一处", () => {
    const second = TEXT.lastIndexOf("为每个监听器");
    const quote = describeQuote(TEXT, second, second + 6);
    expect(locateQuote(TEXT, quote)).toEqual({ start: second, end: second + 6 });
  });

  it("前后文改了一些，原文还在，仍能定位", () => {
    const start = TEXT.indexOf("处理成功");
    const quote = describeQuote(TEXT, start, start + 8);
    const edited = TEXT.replace("发布事件时，", "发布一个事件的时候，");
    expect(locateQuote(edited, quote)).toEqual({
      start: edited.indexOf("处理成功"),
      end: edited.indexOf("处理成功") + 8,
    });
  });

  it("原文改掉了就定位不到", () => {
    const start = TEXT.indexOf("处理成功");
    const quote = describeQuote(TEXT, start, start + 8);
    expect(locateQuote(TEXT.replace("才算完成", "就算完成"), quote)).toBeNull();
  });
});
