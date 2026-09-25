import { act, fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import {
  type BrowseQueryBase,
  BrowseQueryContext,
  type BrowseQueryContextValue,
  BrowseQueryProvider,
  useBrowseQuery,
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

function Probe() {
  const { query, navigate, hrefFor } = useBrowseQuery<TestQuery>();
  return (
    <div>
      <output aria-label="tags">{query.tags.join(",")}</output>
      <button
        type="button"
        onClick={() => navigate((cur) => ({ ...cur, tags: [...cur.tags, "a"], page: 1 }))}
      >
        加 a
      </button>
      <button
        type="button"
        onClick={() => navigate((cur) => ({ ...cur, tags: [...cur.tags, "b"], page: 1 }))}
      >
        加 b
      </button>
      <button type="button" onClick={() => navigate(EMPTY, { replace: true })}>
        清空
      </button>
      <a href={hrefFor({ ...query, page: 2 })}>第二页</a>
    </div>
  );
}

/** 保存某一时刻拿到的 navigate，模拟防抖定时器等延迟回调持有旧引用。 */
let savedNavigate: BrowseQueryContextValue<TestQuery>["navigate"] | null = null;

function Saver() {
  const { navigate } = useBrowseQuery<TestQuery>();
  return (
    <button
      type="button"
      onClick={() => {
        savedNavigate = navigate;
      }}
    >
      保存
    </button>
  );
}

afterEach(() => {
  mockPush.mockClear();
  mockReplace.mockClear();
  savedNavigate = null;
});

describe("BrowseQueryProvider", () => {
  it("navigate 后立即反映乐观值，并 push 对应 URL", () => {
    render(withProvider(<Probe />));
    fireEvent.click(screen.getByRole("button", { name: "加 a" }));
    expect(screen.getByLabelText("tags")).toHaveTextContent("a");
    expect(mockPush).toHaveBeenLastCalledWith("/items?tag=a");
  });

  it("连点两次：第二次基于最新乐观值叠加，不丢第一次", () => {
    render(withProvider(<Probe />));
    fireEvent.click(screen.getByRole("button", { name: "加 a" }));
    fireEvent.click(screen.getByRole("button", { name: "加 b" }));
    expect(screen.getByLabelText("tags")).toHaveTextContent("a,b");
    expect(mockPush).toHaveBeenLastCalledWith("/items?tag=a&tag=b");
  });

  it("replace 选项走 router.replace；空查询回到 basePath", () => {
    render(withProvider(<Probe />, { ...EMPTY, tags: ["x"] }));
    fireEvent.click(screen.getByRole("button", { name: "清空" }));
    expect(mockReplace).toHaveBeenCalledWith("/items");
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("服务端 query 变化（导航完成 / 后退）后乐观值失效", () => {
    const { rerender } = render(withProvider(<Probe />));
    fireEvent.click(screen.getByRole("button", { name: "加 a" }));
    rerender(withProvider(<Probe />, { ...EMPTY, tags: ["x"] }));
    expect(screen.getByLabelText("tags")).toHaveTextContent("x");
  });

  it("导航完成后再后退到原查询：旧乐观值不会复活", () => {
    const { rerender } = render(withProvider(<Probe />));
    fireEvent.click(screen.getByRole("button", { name: "加 a" }));
    rerender(withProvider(<Probe />, { ...EMPTY, tags: ["a"] })); // 服务端确认 A → B
    rerender(withProvider(<Probe />)); // 浏览器后退回 A
    expect(screen.getByLabelText("tags")).toHaveTextContent("");
  });

  it("延迟回调持有旧 navigate 引用时，仍基于最新查询计算", () => {
    render(
      withProvider(
        <>
          <Probe />
          <Saver />
        </>,
      ),
    );
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    fireEvent.click(screen.getByRole("button", { name: "加 a" }));
    act(() => savedNavigate?.((cur) => ({ ...cur, tags: [...cur.tags, "c"] })));
    expect(mockPush).toHaveBeenLastCalledWith("/items?tag=a&tag=c");
  });

  it("hrefFor 拼出 basePath + querystring", () => {
    render(withProvider(<Probe />, { ...EMPTY, q: "nature" }));
    expect(screen.getByRole("link", { name: "第二页" })).toHaveAttribute(
      "href",
      "/items?q=nature&page=2",
    );
  });

  it("在 Provider 外使用 useBrowseQuery 抛错", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Probe />)).toThrow("useBrowseQuery 必须在 BrowseQueryProvider 内使用");
    spy.mockRestore();
  });

  it("withPending 辅助：固定 context 值可直接注入", () => {
    render(withPending(<Probe />, { ...EMPTY, tags: ["p"] }));
    expect(screen.getByLabelText("tags")).toHaveTextContent("p");
  });
});
