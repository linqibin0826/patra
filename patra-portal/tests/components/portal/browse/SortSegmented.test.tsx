import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SortSegmented } from "@/components/portal/browse/SortSegmented";

const OPTIONS = [
  { id: "latest", label: "最近更新", desc: true },
  { id: "year", label: "年份", desc: true },
] as const;

describe("SortSegmented", () => {
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
