import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MeshInterestPicker } from "@/components/settings/profile/MeshInterestPicker";

type Interest = { ui: string; name: string };

const HF = { ui: "D006333", name: "Heart Failure" };
const AF = { ui: "D001281", name: "Atrial Fibrillation" };
const HT = { ui: "D006973", name: "Hypertension" };
const NEOPLASMS = { ui: "D009369", name: "Neoplasms", matchedTerm: "Cancer", treeNumbers: ["C04"] };

const fetchMock = vi.fn();
let latest: Interest[] = [];

function Harness({ initial, errors }: { initial: Interest[]; errors?: Record<number, string> }) {
  const [value, setValue] = useState(initial);
  return (
    <MeshInterestPicker
      id="mesh"
      value={value}
      errors={errors}
      onChange={(next) => {
        latest = next;
        setValue(next);
      }}
    />
  );
}

function renderPicker(initial: Interest[] = [HF, AF], errors?: Record<number, string>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <Harness initial={initial} errors={errors} />
    </QueryClientProvider>,
  );
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

async function typeAndSettle(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(screen.getByRole("combobox", { name: "搜索 MeSH 主题词" }), text);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(300);
  });
}

/// 窄屏与否：选择器按 `(max-width: 880px)` 切换排序方式
function setNarrow(narrow: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: narrow && query === "(max-width: 880px)",
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

const chips = () =>
  within(screen.getByRole("list", { name: "已选主题词，可排序" }))
    .getAllByRole("listitem")
    .map((item) => item.textContent);

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.stubGlobal("fetch", fetchMock);
  setNarrow(false);
  fetchMock.mockImplementation(async () =>
    Response.json({ items: [NEOPLASMS, { ...HF, matchedTerm: null, treeNumbers: ["C14"] }] }),
  );
  latest = [];
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("MeshInterestPicker · 搜索", () => {
  it("含中文：不请求，提示只支持英文", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "心衰");
    expect(screen.getByText("关注领域只支持英文主题词，请用英文搜索")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("不足 2 个字符：不请求", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "h");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("候选：主行英文名，经入口词命中时副行 via，右侧大类；已选的禁用并标已添加", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "cancer");
    expect(fetchMock).toHaveBeenCalledWith("/api/mesh/suggest?q=cancer");
    const neoplasms = await screen.findByRole("option", { name: /Neoplasms/ });
    expect(neoplasms).toHaveTextContent("via Cancer");
    expect(neoplasms).toHaveTextContent("疾病");
    const added = screen.getByRole("option", { name: /Heart Failure/ });
    expect(added).toHaveAttribute("aria-disabled", "true");
    expect(added).toHaveTextContent("已添加");
  });

  it("选中：加到末尾，输入清空", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "cancer");
    await user.click(await screen.findByRole("option", { name: /Neoplasms/ }));
    expect(latest).toEqual([HF, AF, { ui: "D009369", name: "Neoplasms" }]);
    expect(screen.getByRole("combobox", { name: "搜索 MeSH 主题词" })).toHaveValue("");
  });

  it("键盘选中（↓ + 回车）后输入框清空，不回填主题词名", async () => {
    const user = renderPicker();
    await typeAndSettle(user, "cancer");
    await screen.findByRole("option", { name: /Neoplasms/ });
    await user.keyboard("{ArrowDown}{Enter}");
    expect(latest).toEqual([HF, AF, { ui: "D009369", name: "Neoplasms" }]);
    expect(screen.getByRole("combobox", { name: "搜索 MeSH 主题词" })).toHaveValue("");
  });

  it("没有结果、接口失败各有提示", async () => {
    fetchMock.mockImplementationOnce(async () => Response.json({ items: [] }));
    const user = renderPicker();
    await typeAndSettle(user, "zzz");
    expect(await screen.findByText("没有找到匹配的主题词")).toBeInTheDocument();
    fetchMock.mockImplementation(async () => Response.json({ items: [] }, { status: 502 }));
    await typeAndSettle(user, "y");
    expect(await screen.findByText("主题词搜索暂时不可用，请稍后再试")).toBeInTheDocument();
  });

  it("满 10 个：输入框禁用，占位换成提示", () => {
    renderPicker(Array.from({ length: 10 }, (_, i) => ({ ui: `D00000${i}`, name: `Term ${i}` })));
    const input = screen.getByRole("combobox", { name: "搜索 MeSH 主题词" });
    expect(input).toBeDisabled();
    expect(input).toHaveAttribute("placeholder", "最多 10 个，先移除一些再添加");
  });
});

describe("MeshInterestPicker · 已选列表", () => {
  it("移除", async () => {
    const user = renderPicker();
    await user.click(screen.getByRole("button", { name: "移除 Heart Failure" }));
    expect(latest).toEqual([AF]);
  });

  it("键盘排序：聚焦后 Alt+→ / Alt+↓ 后移，Alt+← / Alt+↑ 前移", async () => {
    const user = renderPicker([HF, AF, HT]);
    screen.getByRole("button", { name: "调整 Heart Failure 的位置" }).focus();
    await user.keyboard("{Alt>}{ArrowRight}{/Alt}");
    expect(chips()).toEqual(["Atrial Fibrillation", "Heart Failure", "Hypertension"]);
    await user.keyboard("{Alt>}{ArrowDown}{/Alt}");
    expect(latest.map((i) => i.name)).toEqual([
      "Atrial Fibrillation",
      "Hypertension",
      "Heart Failure",
    ]);
    await user.keyboard("{Alt>}{ArrowUp}{/Alt}");
    expect(latest.map((i) => i.name)).toEqual([
      "Atrial Fibrillation",
      "Heart Failure",
      "Hypertension",
    ]);
  });

  it("窄屏：手柄按钮打开菜单，上移 / 下移 / 移除", async () => {
    setNarrow(true);
    const user = renderPicker([HF, AF]);
    await user.click(screen.getByRole("button", { name: "调整 Atrial Fibrillation 的位置" }));
    await user.click(await screen.findByRole("menuitem", { name: "上移" }));
    expect(latest.map((i) => i.name)).toEqual(["Atrial Fibrillation", "Heart Failure"]);
  });

  it("某一项出错：胶囊描红，下方写原因", () => {
    renderPicker([HF, AF], { 1: "这个主题词已不可用，请移除" });
    const items = within(screen.getByRole("list", { name: "已选主题词，可排序" })).getAllByRole(
      "listitem",
    );
    expect(items[1]?.firstElementChild).toHaveClass("border-status-danger");
    expect(screen.getByText("Atrial Fibrillation：这个主题词已不可用，请移除")).toBeInTheDocument();
  });
});

describe("MeshInterestPicker · 服务端渲染", () => {
  it("拖动排序的读屏说明 id 稳定：两次服务端渲染输出相同（否则水合不一致）", async () => {
    const { renderToString } = await import("react-dom/server");
    const html = () =>
      renderToString(
        <QueryClientProvider client={new QueryClient()}>
          <MeshInterestPicker id="mesh" value={[HF, AF]} onChange={() => {}} />
        </QueryClientProvider>,
      );
    expect(html()).toBe(html());
  });

  it("读屏的拖动说明是中文", () => {
    renderPicker([HF]);
    expect(document.body.textContent).not.toContain("To pick up a draggable item");
    expect(document.body.textContent).toContain("Alt + 方向键");
  });
});

describe("MeshInterestPicker · 窄屏切换", () => {
  it("宽窄切换时不报 React 警告（拖动传感器保持不变）", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const client = new QueryClient();
    const view = (
      <QueryClientProvider client={client}>
        <MeshInterestPicker id="mesh" value={[HF, AF]} onChange={() => {}} />
      </QueryClientProvider>
    );
    const { rerender } = render(view);
    setNarrow(true);
    rerender(
      <QueryClientProvider client={client}>
        <MeshInterestPicker id="mesh" value={[HF, AF, HT]} onChange={() => {}} />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: "调整 Hypertension 的位置" })).toHaveAttribute(
      "aria-haspopup",
      "menu",
    );
    expect(
      error.mock.calls.map((call) => String(call[0])).filter((m) => m.includes("changed size")),
    ).toEqual([]);
    error.mockRestore();
  });
});
