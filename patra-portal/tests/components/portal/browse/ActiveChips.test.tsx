import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ActiveChips } from "@/components/portal/browse/ActiveChips";
import { BrowseQueryProvider } from "@/components/portal/browse/BrowseQueryProvider";

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

afterEach(() => mockPush.mockClear());

const CHIPS = [
  { key: "tag-a", group: "标签", label: "甲", next: { ...EMPTY, tags: ["b"] } },
  { key: "tag-b", group: "标签", label: "乙", next: { ...EMPTY, tags: ["a"] } },
];

describe("ActiveChips", () => {
  it("无 chip 时不渲染", () => {
    const { container } = render(withProvider(<ActiveChips chips={[]} clearAll={EMPTY} />));
    expect(container).toBeEmptyDOMElement();
  });

  it("渲染组名与文案", () => {
    render(withProvider(<ActiveChips chips={CHIPS} clearAll={EMPTY} />));
    expect(screen.getByRole("list", { name: "已选筛选条件" })).toBeInTheDocument();
    expect(screen.getByText("甲")).toBeInTheDocument();
    expect(screen.getAllByText("标签")).toHaveLength(2);
  });

  it("点 × 跳到该 chip 的 next", () => {
    render(withProvider(<ActiveChips chips={CHIPS} clearAll={EMPTY} />));
    fireEvent.click(screen.getByRole("button", { name: "移除 甲" }));
    expect(mockPush).toHaveBeenCalledWith("/items?tag=b");
  });

  it("清除全部跳到 clearAll", () => {
    render(withProvider(<ActiveChips chips={CHIPS} clearAll={EMPTY} />));
    fireEvent.click(screen.getByRole("button", { name: "清除全部" }));
    expect(mockPush).toHaveBeenCalledWith("/items");
  });

  it("scrollOnMobile：移动端单行横向滚动", () => {
    render(withProvider(<ActiveChips chips={CHIPS} clearAll={EMPTY} scrollOnMobile />));
    expect(screen.getByRole("list", { name: "已选筛选条件" }).className).toMatch(
      /max-md:overflow-x-auto/,
    );
  });
});
