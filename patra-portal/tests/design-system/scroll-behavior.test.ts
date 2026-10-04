import { describe, expect, it } from "vitest";
import { readSrc } from "./source-scan";

describe("换页时的滚动", () => {
  // globals.css 给 html 开了平滑滚动（供页内锚点用）。Next 16 起换页回顶时不再临时关掉它，
  // 要在 <html> 上声明 data-scroll-behavior="smooth"，换页才是瞬间回顶，而不是从旧位置慢慢滚上去。
  it("全局开着平滑滚动时，<html> 声明 data-scroll-behavior，换页瞬间回顶", () => {
    expect(readSrc("app/globals.css")).toMatch(/html\s*\{\s*scroll-behavior:\s*smooth;/);
    expect(readSrc("app/layout.tsx")).toMatch(/<html[^>]*data-scroll-behavior="smooth"/);
  });
});
