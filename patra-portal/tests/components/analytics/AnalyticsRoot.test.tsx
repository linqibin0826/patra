import { act, render } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AnalyticsRoot } from "@/components/analytics/AnalyticsRoot";
import { flushWithBeacon, track } from "@/lib/analytics/client/queue";

let pathname = "/";
vi.mock("next/navigation", () => ({ usePathname: () => pathname }));
vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn(), flushWithBeacon: vi.fn() }));

let visibility: DocumentVisibilityState = "visible";
const setVisibility = (state: DocumentVisibilityState) =>
  act(() => {
    visibility = state;
    document.dispatchEvent(new Event("visibilitychange"));
  });

beforeEach(() => {
  pathname = "/";
  visibility = "visible";
  vi.spyOn(document, "visibilityState", "get").mockImplementation(() => visibility);
  vi.mocked(track).mockReset();
  vi.mocked(flushWithBeacon).mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

const pageViews = () => vi.mocked(track).mock.calls.filter(([name]) => name === "page_viewed");

describe("Page Viewed", () => {
  it("首次加载、页面可见：发一条", () => {
    render(<AnalyticsRoot />);
    expect(track).toHaveBeenCalledWith("page_viewed", {});
    expect(pageViews()).toHaveLength(1);
  });

  it("StrictMode 下首次加载也只发一条", () => {
    render(
      <StrictMode>
        <AnalyticsRoot />
      </StrictMode>,
    );
    expect(pageViews()).toHaveLength(1);
  });

  it("站内跳到别的路径：再发一条；同一路径重渲染不发", () => {
    const { rerender } = render(<AnalyticsRoot />);
    rerender(<AnalyticsRoot />);
    expect(pageViews()).toHaveLength(1);
    pathname = "/papers/42";
    rerender(<AnalyticsRoot />);
    expect(pageViews()).toHaveLength(2);
  });

  it("在后台标签页里打开：等第一次可见再发", () => {
    visibility = "hidden";
    render(<AnalyticsRoot />);
    expect(pageViews()).toHaveLength(0);
    setVisibility("visible");
    expect(pageViews()).toHaveLength(1);
    setVisibility("hidden");
    setVisibility("visible");
    expect(pageViews()).toHaveLength(1);
  });

  it("可见之前又换了路径：只为当前路径发一次", () => {
    visibility = "hidden";
    const { rerender } = render(<AnalyticsRoot />);
    pathname = "/papers";
    rerender(<AnalyticsRoot />);
    setVisibility("visible");
    expect(pageViews()).toHaveLength(1);
  });
});

describe("离开页面时补发", () => {
  it("变为隐藏时用 sendBeacon 发出剩下的", () => {
    render(<AnalyticsRoot />);
    setVisibility("hidden");
    expect(flushWithBeacon).toHaveBeenCalledTimes(1);
  });

  it("pagehide 兜底", () => {
    render(<AnalyticsRoot />);
    act(() => void window.dispatchEvent(new Event("pagehide")));
    expect(flushWithBeacon).toHaveBeenCalledTimes(1);
  });
});
