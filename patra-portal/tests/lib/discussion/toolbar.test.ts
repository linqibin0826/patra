import { describe, expect, it } from "vitest";
import { applyToolbar } from "@/lib/discussion/toolbar";

const at = (start: number, end = start) => ({ start, end });

describe("applyToolbar", () => {
  it.each([
    ["bold", "**", 2],
    ["italic", "*", 1],
    ["code", "`", 1],
  ] as const)("%s：有选区包住", (action, mark, width) => {
    expect(applyToolbar("看 样本 量", at(2, 4), action)).toEqual({
      value: `看 ${mark}样本${mark} 量`,
      selection: at(2 + width, 4 + width),
    });
  });

  it("bold：没有选区插入成对标记，光标放中间", () => {
    expect(applyToolbar("ab", at(1), "bold")).toEqual({ value: "a****b", selection: at(3) });
  });

  it("link：包成 [选区](https://) 并选中网址", () => {
    expect(applyToolbar("见原文", at(1, 3), "link")).toEqual({
      value: "见[原文](https://)",
      selection: at(6, 14),
    });
  });

  it("quote：选中的每一行加前缀", () => {
    expect(applyToolbar("a\nb\nc", at(0, 3), "quote")).toEqual({
      value: "> a\n> b\nc",
      selection: at(0, 7),
    });
  });

  it("ordered：按行编号", () => {
    expect(applyToolbar("a\nb", at(0, 3), "ordered").value).toBe("1. a\n2. b");
  });

  it("bullet：没有选区时光标落在前缀之后", () => {
    expect(applyToolbar("x\n", at(2), "bullet")).toEqual({ value: "x\n- ", selection: at(4) });
  });

  it("mention：插入 @，光标在其后", () => {
    expect(applyToolbar("请 看", at(2), "mention")).toEqual({ value: "请 @看", selection: at(3) });
  });
});
