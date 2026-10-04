import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { useScrollFade } from "@/lib/use-scroll-fade";

function Probe() {
  const { ref, fadeEnd } = useScrollFade<HTMLElement>();
  return <section ref={ref} aria-label="滚动区" data-fade-end={fadeEnd || undefined} />;
}

/// jsdom 不做布局：用实例属性模拟滚动容器的尺寸与位置
function mockBox(
  el: HTMLElement,
  box: { scrollWidth: number; clientWidth: number; scrollLeft: number },
) {
  for (const [key, value] of Object.entries(box)) {
    Object.defineProperty(el, key, { value, configurable: true, writable: true });
  }
}

describe("useScrollFade", () => {
  const originalScrollWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollWidth");
  afterEach(() => {
    if (originalScrollWidth) {
      Object.defineProperty(HTMLElement.prototype, "scrollWidth", originalScrollWidth);
    }
  });

  it("内容放得下时不渐隐", () => {
    render(<Probe />);
    expect(screen.getByRole("region", { name: "滚动区" })).not.toHaveAttribute("data-fade-end");
  });

  it("右侧还有内容时渐隐；滚到尽头后取消", () => {
    // 挂载时就已溢出：在原型上给出超宽的 scrollWidth
    Object.defineProperty(HTMLElement.prototype, "scrollWidth", {
      configurable: true,
      get: () => 300,
    });
    render(<Probe />);
    const region = screen.getByRole("region", { name: "滚动区" });
    expect(region).toHaveAttribute("data-fade-end");

    mockBox(region, { scrollWidth: 300, clientWidth: 100, scrollLeft: 200 });
    act(() => {
      fireEvent.scroll(region);
    });
    expect(region).not.toHaveAttribute("data-fade-end");
  });
});
