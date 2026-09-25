import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const css = readFileSync(join(import.meta.dirname, "globals.css"), "utf8");

describe("页面边界回弹", () => {
  it("桌面端（细指针且无触屏）关闭根元素纵向回弹，滚到顶 / 底时整页不被拖动", () => {
    expect(css).toMatch(
      /@media \(hover: hover\) and \(pointer: fine\) and \(not \(any-pointer: coarse\)\)\s*\{\s*html\s*\{\s*overscroll-behavior-y:\s*none;\s*\}\s*\}/,
    );
  });

  it("触屏设备（含带触控板的 iPad、触屏笔记本）保留回弹与下拉刷新（只在上述媒体查询里出现 overscroll-behavior）", () => {
    expect(css.match(/overscroll-behavior/g)?.length ?? 0).toBe(1);
  });
});
