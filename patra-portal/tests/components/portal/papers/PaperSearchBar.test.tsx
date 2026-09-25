import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperSearchBar } from "@/components/portal/papers/PaperSearchBar";
import { PapersQueryProvider } from "@/components/portal/papers/PapersQueryProvider";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PaperSearchQuery } from "@/types/portal";

const mockPush = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
}));

function ui(query: PaperSearchQuery) {
  return (
    <PapersQueryProvider query={query}>
      <PaperSearchBar />
    </PapersQueryProvider>
  );
}

const input = () => screen.getByRole("searchbox");
const submit = () => fireEvent.click(screen.getByRole("button", { name: "检索" }));

describe("PaperSearchBar", () => {
  beforeEach(() => mockPush.mockClear());

  it("初始 tab：有 pmid 选 PMID；只有 author 选作者；其余选关键词", () => {
    const { unmount } = render(ui({ ...EMPTY_PAPER_QUERY, pmid: "41605285" }));
    expect(screen.getByRole("button", { name: "PMID" })).toHaveAttribute("aria-pressed", "true");
    expect(input()).toHaveValue("41605285");
    unmount();
    render(ui({ ...EMPTY_PAPER_QUERY, author: "Topol" }));
    expect(screen.getByRole("button", { name: "作者" })).toHaveAttribute("aria-pressed", "true");
    expect(input()).toHaveValue("Topol");
  });

  it("切 tab 只换输入框内容与占位，不导航", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, q: "GLP-1", author: "Smith" }));
    fireEvent.click(screen.getByRole("button", { name: "作者" }));
    expect(input()).toHaveValue("Smith");
    fireEvent.click(screen.getByRole("button", { name: "DOI" }));
    expect(input()).toHaveValue("");
    expect(input()).toHaveAttribute("placeholder", "10.1016/j.jaci.2024.03.018");
    expect(input().className).toMatch(/font-mono/);
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("关键词提交：保留筛选、回第 1 页", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, type: ["Review"], page: 3 }));
    fireEvent.change(input(), { target: { value: " GLP-1 " } });
    submit();
    expect(mockPush).toHaveBeenCalledWith("/papers?q=GLP-1&type=Review");
  });

  it("从精确定位切回关键词提交：清掉 pmid", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, pmid: "999" }));
    fireEvent.click(screen.getByRole("button", { name: /关键词/ }));
    fireEvent.change(input(), { target: { value: "GLP-1" } });
    submit();
    expect(mockPush).toHaveBeenCalledWith("/papers?q=GLP-1");
  });

  it("PMID 提交：清空其余条件", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, q: "x", type: ["Review"] }));
    fireEvent.click(screen.getByRole("button", { name: "PMID" }));
    fireEvent.change(input(), { target: { value: "41605285" } });
    submit();
    expect(mockPush).toHaveBeenCalledWith("/papers?pmid=41605285");
  });

  it("PMID 非纯数字：框下提示且不导航；再输入时提示消失", () => {
    render(ui(EMPTY_PAPER_QUERY));
    fireEvent.click(screen.getByRole("button", { name: "PMID" }));
    fireEvent.change(input(), { target: { value: "abc" } });
    submit();
    expect(screen.getByRole("alert")).toHaveTextContent("PMID 应为纯数字");
    expect(input()).toHaveAttribute("aria-invalid", "true");
    expect(mockPush).not.toHaveBeenCalled();
    fireEvent.change(input(), { target: { value: "1" } });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("超长关键词：提示且不导航（不静默变成全库浏览）", () => {
    render(ui(EMPTY_PAPER_QUERY));
    fireEvent.change(input(), { target: { value: "a".repeat(201) } });
    submit();
    expect(screen.getByRole("alert")).toHaveTextContent("检索词最长 200 字");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("清除按钮：关键词 tab 下同时移除 q", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, q: "GLP-1" }));
    fireEvent.click(screen.getByRole("button", { name: "清除输入" }));
    expect(input()).toHaveValue("");
    expect(mockPush).toHaveBeenCalledWith("/papers");
  });

  it("切换排序：push sort=year 并回第 1 页", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, page: 4 }));
    fireEvent.click(screen.getByRole("button", { name: /年份/ }));
    expect(mockPush).toHaveBeenCalledWith("/papers?sort=year");
  });

  it("外部 query 变化（chip 移除 / 后退）时同步输入框", () => {
    const { rerender } = render(ui({ ...EMPTY_PAPER_QUERY, q: "GLP-1" }));
    rerender(ui(EMPTY_PAPER_QUERY));
    expect(input()).toHaveValue("");
  });

  it("移动端筛选按钮角标 = 已选筛选项数（不含关键词）", () => {
    render(ui({ ...EMPTY_PAPER_QUERY, q: "x", type: ["A", "B"] }));
    expect(within(screen.getByRole("button", { name: "筛选" })).getByText("2")).toBeInTheDocument();
  });
});
