import { act, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FulltextReadTracker } from "@/components/analytics/FulltextReadTracker";
import { track } from "@/lib/analytics/client/queue";

vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));

let visibility: DocumentVisibilityState = "visible";
/// 正文（全部子元素合起来）在视口里的位置
let rect = { top: 300, bottom: 3_000 };

function Body() {
  return (
    <>
      <section>引言</section>
      <section>方法</section>
    </>
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(track).mockReset();
  visibility = "visible";
  rect = { top: 300, bottom: 3_000 };
  vi.spyOn(document, "visibilityState", "get").mockImplementation(() => visibility);
  vi.stubGlobal("innerHeight", 1_000);
  vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb) => {
    cb(0);
    return 0;
  });
  // 每个段落都报同一个矩形：合起来就是正文的位置
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
    () => ({ top: rect.top, bottom: rect.bottom, height: rect.bottom - rect.top }) as DOMRect,
  );
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

const scroll = () => act(() => void window.dispatchEvent(new Event("scroll")));
const setVisibility = (state: DocumentVisibilityState) =>
  act(() => {
    visibility = state;
    document.dispatchEvent(new Event("visibilitychange"));
  });

describe("FulltextReadTracker", () => {
  it("正文占视口一半以上、前台累计 10 秒：发一条 fulltext_read", () => {
    render(
      <FulltextReadTracker articleId="42">
        <Body />
      </FulltextReadTracker>,
    );
    act(() => vi.advanceTimersByTime(10_000));
    expect(track).toHaveBeenCalledWith("fulltext_read", { article_id: "42", visible_seconds: 10 });
  });

  it("切到后台不累计，回来接着算", () => {
    render(
      <FulltextReadTracker articleId="42">
        <Body />
      </FulltextReadTracker>,
    );
    act(() => vi.advanceTimersByTime(6_000));
    setVisibility("hidden");
    act(() => vi.advanceTimersByTime(60_000));
    expect(track).not.toHaveBeenCalled();
    setVisibility("visible");
    act(() => vi.advanceTimersByTime(4_000));
    expect(track).toHaveBeenCalledTimes(1);
  });

  it("滚到正文只露出底部一点：暂停计时", () => {
    render(
      <FulltextReadTracker articleId="42">
        <Body />
      </FulltextReadTracker>,
    );
    act(() => vi.advanceTimersByTime(5_000));
    rect = { top: 800, bottom: 3_000 };
    scroll();
    act(() => vi.advanceTimersByTime(30_000));
    expect(track).not.toHaveBeenCalled();
  });

  it("换一篇文献（重新挂载）重新计时", () => {
    const { rerender } = render(
      <FulltextReadTracker key="42" articleId="42">
        <Body />
      </FulltextReadTracker>,
    );
    act(() => vi.advanceTimersByTime(6_000));
    rerender(
      <FulltextReadTracker key="43" articleId="43">
        <Body />
      </FulltextReadTracker>,
    );
    act(() => vi.advanceTimersByTime(6_000));
    expect(track).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(4_000));
    expect(track).toHaveBeenCalledWith("fulltext_read", { article_id: "43", visible_seconds: 10 });
  });
});
