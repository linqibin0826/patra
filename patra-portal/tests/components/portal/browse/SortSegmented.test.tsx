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

  it("点击触发 onChange(id)", () => {
    const onChange = vi.fn();
    render(<SortSegmented options={OPTIONS} value="latest" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /年份/ }));
    expect(onChange).toHaveBeenCalledWith("year");
  });
});
