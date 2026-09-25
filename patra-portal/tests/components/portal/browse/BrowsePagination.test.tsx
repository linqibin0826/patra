import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { BrowsePagination, pageWindow } from "@/components/portal/browse/BrowsePagination";
import {
  type BrowseQueryBase,
  BrowseQueryContext,
  type BrowseQueryContextValue,
  BrowseQueryProvider,
} from "@/components/portal/browse/BrowseQueryProvider";

const mockPush = vi.fn<(url: string) => void>();
const mockReplace = vi.fn<(url: string) => void>();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

interface TestQuery {
  q: string;
  tags: string[];
  page: number;
}

const EMPTY: TestQuery = { q: "", tags: [], page: 1 };

function serialize(query: TestQuery): string {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  for (const tag of query.tags) params.append("tag", tag);
  if (query.page > 1) params.set("page", String(query.page));
  return params.toString();
}

function withProvider(ui: ReactNode, query: TestQuery = EMPTY) {
  return (
    <BrowseQueryProvider query={query} basePath="/items" serialize={serialize}>
      {ui}
    </BrowseQueryProvider>
  );
}

/** 以固定 context 值渲染（模拟导航进行中）。 */
function withPending(ui: ReactNode, query: TestQuery = EMPTY) {
  const value: BrowseQueryContextValue<BrowseQueryBase> = {
    query,
    isPending: true,
    hrefFor: (q) => `/items?${serialize(q as TestQuery)}`,
    navigate: () => undefined,
  };
  return <BrowseQueryContext.Provider value={value}>{ui}</BrowseQueryContext.Provider>;
}

describe("pageWindow", () => {
  it("total ≤ 7：全列所有页码", () => {
    expect(pageWindow(3, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(pageWindow(1, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it("total > 7，cur 在中间：首尾各一个省略号", () => {
    const result = pageWindow(10, 20);
    expect(result[0]).toBe(1);
    expect(result.at(-1)).toBe(20);
    expect(result.filter((v) => v === "…")).toHaveLength(2);
    expect(result).toContain(10);
  });

  it("cur=1：只有右侧省略号；cur=末页：只有左侧省略号", () => {
    expect(pageWindow(1, 20).filter((v) => v === "…")).toHaveLength(1);
    expect(pageWindow(20, 20).filter((v) => v === "…")).toHaveLength(1);
  });
});

describe("BrowsePagination", () => {
  it("只有 1 页时不渲染", () => {
    const { container } = render(
      withProvider(<BrowsePagination page={1} total={8} pageSize={12} unit="本" />),
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("渲染分页导航与区间文案（千分位）", () => {
    render(withProvider(<BrowsePagination page={1} total={18807} pageSize={20} unit="篇" />));
    expect(screen.getByRole("navigation", { name: "分页" })).toBeInTheDocument();
    expect(screen.getByText("第 1–20 篇 · 共 18,807 篇")).toBeInTheDocument();
  });

  it("首页禁用上一页、末页禁用下一页", () => {
    const { unmount } = render(
      withProvider(<BrowsePagination page={1} total={50} pageSize={12} unit="本" />),
    );
    expect(screen.getByRole("link", { name: "上一页" })).toHaveAttribute("aria-disabled", "true");
    unmount();
    render(withProvider(<BrowsePagination page={5} total={50} pageSize={12} unit="本" />));
    expect(screen.getByRole("link", { name: "下一页" })).toHaveAttribute("aria-disabled", "true");
  });

  it("当前页 aria-current=page；page ≤ 0 裁剪到第 1 页", () => {
    render(withProvider(<BrowsePagination page={0} total={50} pageSize={12} unit="本" />));
    expect(screen.getByRole("link", { name: "1" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByText(/第 1–12 本/)).toBeInTheDocument();
  });

  it("页码链接由 Provider 生成并保留其余条件", () => {
    render(
      withProvider(<BrowsePagination page={1} total={50} pageSize={12} unit="本" />, {
        ...EMPTY,
        q: "nature",
      }),
    );
    expect(screen.getByRole("link", { name: "2" })).toHaveAttribute(
      "href",
      "/items?q=nature&page=2",
    );
  });

  it("导航进行中：整个分页 aria-disabled，链接移出 tab 序列", () => {
    render(withPending(<BrowsePagination page={1} total={50} pageSize={12} unit="本" />));
    const nav = screen.getByRole("navigation", { name: "分页" });
    expect(nav).toHaveAttribute("aria-disabled", "true");
    expect(nav.className).toMatch(/pointer-events-none/);
    expect(screen.getByRole("link", { name: "2" })).toHaveAttribute("tabindex", "-1");
  });

  it("含省略号时不产生重复 key 警告", () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    render(withProvider(<BrowsePagination page={1} total={120} pageSize={12} unit="本" />));
    expect(
      errorSpy.mock.calls.find((call) => String(call[0]).includes("same key")),
    ).toBeUndefined();
    errorSpy.mockRestore();
  });
});
