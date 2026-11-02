import { readSrc } from "@tests/design-system/source-scan";
import { describe, expect, it } from "vitest";

describe("根布局挂着浏览器埋点入口", () => {
  // Page Viewed 与离开页面时的补发都靠它；放在 QueryProvider 里，与提醒推送同一层
  it("app/layout.tsx 渲染 <AnalyticsRoot />", () => {
    expect(readSrc("app/layout.tsx")).toMatch(/<AnalyticsRoot \/>/);
  });
});
