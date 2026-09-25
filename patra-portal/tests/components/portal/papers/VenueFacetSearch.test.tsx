import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { VenueFacetSearch } from "@/components/portal/papers/VenueFacetSearch";
import type { VenueSuggestion } from "@/types/portal";

const LANCET: VenueSuggestion = { id: "1", name: "The Lancet", abbr: "Lancet" };
const LANCET_ONC: VenueSuggestion = { id: "2", name: "The Lancet Oncology", abbr: "Lancet Oncol" };

function stubSuggest(body: unknown, status = 200) {
  // 每次调用返回新的 Response（body 只能读一次；TanStack 会对 stale 数据后台重取）
  const fetchMock = vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status })));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

/** 按 q 返回不同候选 */
function stubSuggestBy(byQuery: Record<string, VenueSuggestion[]>) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      const q = new URL(url, "http://localhost").searchParams.get("q") ?? "";
      return Promise.resolve(new Response(JSON.stringify(byQuery[q] ?? [])));
    }),
  );
}

function renderSearch(props: Partial<Parameters<typeof VenueFacetSearch>[0]> = {}) {
  const onAdd = vi.fn();
  const onRemove = vi.fn();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <VenueFacetSearch selected={[]} names={{}} onAdd={onAdd} onRemove={onRemove} {...props} />
    </QueryClientProvider>,
  );
  return { onAdd, onRemove, input: screen.getByRole("combobox", { name: "搜索期刊" }) };
}

afterEach(() => vi.unstubAllGlobals());

describe("VenueFacetSearch", () => {
  it("输入后防抖请求候选并展示（已选期刊被排除）", async () => {
    const fetchMock = stubSuggest([LANCET, LANCET_ONC]);
    const { input } = renderSearch({ selected: ["2"] });
    fireEvent.change(input, { target: { value: "lan" } });
    expect(await screen.findByRole("option", { name: /The Lancet/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Oncology/ })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/venues/suggest?q=lan");
  });

  it("点击候选：onAdd 并清空输入", async () => {
    stubSuggest([LANCET]);
    const { input, onAdd } = renderSearch();
    fireEvent.change(input, { target: { value: "lan" } });
    fireEvent.mouseDown(await screen.findByRole("option", { name: /The Lancet/ }));
    expect(onAdd).toHaveBeenCalledWith(LANCET);
    expect(input).toHaveValue("");
  });

  it("键盘：Enter 选当前高亮项，↓ 移到下一项，Esc 关闭", async () => {
    stubSuggest([LANCET, LANCET_ONC]);
    const { input, onAdd } = renderSearch();
    fireEvent.change(input, { target: { value: "lan" } });
    await screen.findByRole("option", { name: /Oncology/ });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(screen.getByRole("option", { name: /Oncology/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onAdd).toHaveBeenCalledWith(LANCET_ONC);

    fireEvent.change(input, { target: { value: "lan" } });
    await screen.findByRole("option", { name: /Oncology/ });
    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("输入已变、候选尚未更新时回车不会选中旧候选", async () => {
    stubSuggestBy({ lan: [LANCET], cell: [] });
    const { input, onAdd } = renderSearch();
    fireEvent.change(input, { target: { value: "lan" } });
    await screen.findByRole("option", { name: /The Lancet/ });
    fireEvent.change(input, { target: { value: "cell" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onAdd).not.toHaveBeenCalled();
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
  });

  it("候选变少后高亮夹回有效项（如候选在别处被选中）", async () => {
    stubSuggest([LANCET, LANCET_ONC]);
    const onAdd = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = (selected: string[]) => (
      <QueryClientProvider client={client}>
        <VenueFacetSearch selected={selected} names={{}} onAdd={onAdd} onRemove={() => {}} />
      </QueryClientProvider>
    );
    const { rerender } = render(view([]));
    const input = screen.getByRole("combobox", { name: "搜索期刊" });
    fireEvent.change(input, { target: { value: "lan" } });
    await screen.findByRole("option", { name: /Oncology/ });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    rerender(view(["2"]));
    expect(screen.getByRole("option", { name: /The Lancet/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onAdd).toHaveBeenCalledWith(LANCET);
  });

  it("无候选 → 未找到匹配期刊", async () => {
    stubSuggest([]);
    const first = renderSearch();
    fireEvent.change(first.input, { target: { value: "zzz" } });
    expect(await screen.findByText("未找到匹配期刊")).toBeInTheDocument();
  });

  it("请求失败 → 候选加载失败", async () => {
    stubSuggest([], 502);
    const { input } = renderSearch();
    fireEvent.change(input, { target: { value: "lan" } });
    expect(await screen.findByText("候选加载失败")).toBeInTheDocument();
  });

  it("已选期刊以勾选行列出（名称缺失回退 期刊 #id），取消勾选触发 onRemove", async () => {
    const { onRemove } = renderSearch({ selected: ["1", "999"], names: { "1": "The Lancet" } });
    expect(screen.getByRole("checkbox", { name: "The Lancet" })).toBeChecked();
    fireEvent.click(screen.getByRole("checkbox", { name: "期刊 #999" }));
    await waitFor(() => expect(onRemove).toHaveBeenCalledWith("999"));
  });
});
