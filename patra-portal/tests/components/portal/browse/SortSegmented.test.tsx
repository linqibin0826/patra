import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SortSegmented } from "@/components/portal/browse/SortSegmented";

const OPTIONS = [
  { id: "latest", label: "最近更新", desc: true },
  { id: "year", label: "年份", desc: true },
] as const;

/** jsdom 不排版：按按钮文字给出假的位置与宽度 */
const LAYOUT: Record<string, { left: number; width: number }> = {
  最近更新: { left: 4, width: 96 },
  年份: { left: 102, width: 64 },
};
const layoutOf = (el: HTMLElement) =>
  LAYOUT[Object.keys(LAYOUT).find((k) => el.textContent?.startsWith(k)) ?? ""];

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SortSegmented", () => {
  it("滑动指示条落在当前项下方，切换后移到新选项", () => {
    vi.spyOn(HTMLElement.prototype, "offsetLeft", "get").mockImplementation(function (
      this: HTMLElement,
    ) {
      return layoutOf(this)?.left ?? 0;
    });
    vi.spyOn(HTMLElement.prototype, "offsetWidth", "get").mockImplementation(function (
      this: HTMLElement,
    ) {
      return layoutOf(this)?.width ?? 0;
    });
    const { container, rerender } = render(
      <SortSegmented options={OPTIONS} value="latest" onChange={() => {}} />,
    );
    const indicator = container.querySelector<HTMLElement>("[data-sort-indicator]");
    expect(indicator).toHaveAttribute("aria-hidden", "true");
    expect(indicator?.style.getPropertyValue("--sort-left")).toBe("4px");
    expect(indicator?.style.getPropertyValue("--sort-width")).toBe("96px");

    rerender(<SortSegmented options={OPTIONS} value="year" onChange={() => {}} />);
    expect(indicator?.style.getPropertyValue("--sort-left")).toBe("102px");
    expect(indicator?.style.getPropertyValue("--sort-width")).toBe("64px");
  });

  it("渲染全部选项，当前项 aria-pressed=true", () => {
    render(<SortSegmented options={OPTIONS} value="year" onChange={() => {}} />);
    expect(screen.getByRole("group", { name: "排序方式" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /年份/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /最近更新/ })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("降序项向辅助技术读出「降序」（↓ 只是图形）", () => {
    render(<SortSegmented options={OPTIONS} value="latest" onChange={() => {}} />);
    expect(screen.getByRole("button", { name: "最近更新，降序" })).toBeInTheDocument();
  });

  it("选项放得下时不加右缘渐隐标记", () => {
    render(<SortSegmented options={OPTIONS} value="latest" onChange={() => {}} />);
    const scroller = screen.getByRole("group", { name: "排序方式" }).parentElement;
    expect(scroller).not.toHaveAttribute("data-fade-end");
  });

  it("点击触发 onChange(id)", () => {
    const onChange = vi.fn();
    render(<SortSegmented options={OPTIONS} value="latest" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /年份/ }));
    expect(onChange).toHaveBeenCalledWith("year");
  });
});
