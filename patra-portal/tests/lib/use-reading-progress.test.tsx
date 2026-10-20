import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { progressIn, remainingMinutes, useReadingProgress } from "@/lib/use-reading-progress";

describe("progressIn", () => {
  it("吸顶线在区间里的位置，夹在 0–1", () => {
    expect(progressIn(100, 300, 200)).toBe(0.5);
    expect(progressIn(300, 500, 200)).toBe(0);
    expect(progressIn(0, 100, 200)).toBe(1);
  });
});

describe("remainingMinutes", () => {
  it("按正文未读比例向上取整", () => {
    expect(remainingMinutes(34, 0.62)).toBe(13);
    expect(remainingMinutes(34, 1)).toBe(0);
  });
});

/** 放一个元素到页面上，指定它此刻的上下边距视口顶部的距离。 */
function place(selector: { id?: string; attr?: string }, top: number, bottom: number) {
  const el = document.createElement("div");
  if (selector.id) el.id = selector.id;
  if (selector.attr) el.setAttribute(selector.attr, "");
  el.getBoundingClientRect = () => ({ top, bottom }) as DOMRect;
  document.body.append(el);
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("useReadingProgress", () => {
  it("正文进度量到声明区为止，整篇进度量 #fulltext 到 #license 底", () => {
    place({ id: "fulltext" }, -100, 2000);
    place({ attr: "data-ft-body" }, 34, 1500);
    place({ id: "declarations" }, 1034, 1300);
    place({ id: "license" }, 1800, 1900);

    const { result } = renderHook(() => useReadingProgress());
    act(() => {
      window.dispatchEvent(new Event("scroll"));
    });

    expect(result.current.body).toBeCloseTo(0.1);
    expect(result.current.whole).toBeCloseTo(234 / 2000);
    expect(result.current.started).toBe(true);
  });

  it("没有声明区时量到参考文献；还没读到正文时 started 为 false", () => {
    place({ id: "fulltext" }, 500, 3000);
    place({ attr: "data-ft-body" }, 600, 2600);
    place({ id: "references" }, 1600, 2600);
    place({ id: "license" }, 2900, 3000);

    const { result } = renderHook(() => useReadingProgress());
    act(() => {
      window.dispatchEvent(new Event("scroll"));
    });

    expect(result.current.body).toBe(0);
    expect(result.current.started).toBe(false);
  });
});
