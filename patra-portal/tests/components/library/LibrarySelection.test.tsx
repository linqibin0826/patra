import { screen, waitFor, within } from "@testing-library/react";
import { makeCollection, makeLibraryItem, makeOverview } from "@tests/fixtures/interaction";
import { makePaper } from "@tests/fixtures/paper";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { LibraryView } from "@/components/library/LibraryView";
import { addToCollection, removeFromCollection } from "@/lib/interaction/collection-actions";
import type { LibraryEntry } from "@/lib/interaction/library";
import { removeItems } from "@/lib/interaction/library-actions";

vi.mock("@/lib/interaction/library-actions", () => ({
  collectPublication: vi.fn(),
  removeItems: vi.fn(),
  saveNote: vi.fn(),
}));
vi.mock("@/lib/interaction/collection-actions", () => ({
  addToCollection: vi.fn(),
  removeFromCollection: vi.fn(),
  createCollection: vi.fn(),
  updateCollection: vi.fn(),
  deleteCollection: vi.fn(),
  setVisibility: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
const push = vi.fn();
let pathname = "/library";
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push, replace: push }),
  usePathname: () => pathname,
}));

const add = vi.mocked(addToCollection);
const removeFrom = vi.mocked(removeFromCollection);
const remove = vi.mocked(removeItems);

beforeEach(() => {
  pathname = "/library";
  add.mockReset();
  removeFrom.mockReset();
  remove.mockReset();
  push.mockReset();
  vi.mocked(toast.success).mockReset();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(null, { status: 502 })),
  );
});

const OVERVIEW = makeOverview({
  collections: [makeCollection({ collectionId: "9", name: "组会" })],
});

const entry = (n: number): LibraryEntry => ({
  item: makeLibraryItem({ itemId: String(500 + n), key: String(10 + n), collectionIds: [] }),
  paper: makePaper({ id: String(10 + n), title: `Paper ${n}` }),
  stats: null,
});

function renderView(view = "all", page = 1, entries = [entry(1), entry(2)]) {
  return renderWithLibrary(<LibraryView view={view} sort="added" page={page} />, {
    overview: OVERVIEW,
    items: {
      view,
      sort: "added",
      page,
      data: { page, pageSize: 50, total: 100 + entries.length, totalPages: 3, items: entries },
    },
  });
}

async function selectBoth(user: ReturnType<typeof renderView>["user"]) {
  await user.click(screen.getByRole("button", { name: "选择" }));
  await user.click(screen.getByRole("checkbox", { name: "选择「Paper 1」" }));
  await user.click(screen.getByRole("checkbox", { name: "选择「Paper 2」" }));
}

describe("选择模式与批量栏", () => {
  it("进入选择模式：每条有复选框，工具条换成全选本页 · 已选 · 完成", async () => {
    const { user } = renderView();
    await user.click(screen.getByRole("button", { name: "选择" }));
    expect(screen.getByRole("checkbox", { name: "全选本页" })).toBeInTheDocument();
    expect(screen.getByText("已选 0 篇")).toBeInTheDocument();
    expect(screen.getAllByRole("checkbox", { name: /^选择「/ })).toHaveLength(2);
    expect(screen.queryByRole("toolbar")).not.toBeInTheDocument();
  });

  it("选中后出现批量栏，读屏播报已选篇数", async () => {
    const { user } = renderView();
    await user.click(screen.getByRole("button", { name: "选择" }));
    await user.click(screen.getByRole("checkbox", { name: "选择「Paper 1」" }));
    const bar = screen.getByRole("toolbar", { name: "批量操作" });
    expect(
      within(bar)
        .getAllByRole("button")
        .map((b) => b.textContent),
    ).toEqual(["放进收藏夹…", "复制引用", "导出", "移除"]);
    expect(screen.getByText("已选 1 篇", { selector: "[aria-live]" })).toBeInTheDocument();
  });

  it("全选本页、完成", async () => {
    const { user } = renderView();
    await user.click(screen.getByRole("button", { name: "选择" }));
    await user.click(screen.getByRole("checkbox", { name: "全选本页" }));
    expect(screen.getAllByText("已选 2 篇").length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "完成" }));
    expect(screen.queryByRole("checkbox", { name: /^选择「/ })).not.toBeInTheDocument();
  });

  it("批量放进收藏夹：toast 篇数；部分因满没放进，提示条", async () => {
    add.mockResolvedValue({
      ok: true,
      result: { added: 1, alreadyIn: 0, skippedFull: 1, skippedNotInLibrary: 0 },
    });
    const { user } = renderView();
    await selectBoth(user);
    await user.click(screen.getByRole("button", { name: "放进收藏夹…" }));
    await user.click(await screen.findByRole("checkbox", { name: /组会/ }));
    await waitFor(() => expect(add).toHaveBeenCalledWith("9", ["501", "502"]));
    expect(toast.success).toHaveBeenCalledWith("已放进「组会」1 篇");
    expect(
      await screen.findByText("1 篇没有放进「组会」：收藏夹已满 5,000 篇。"),
    ).toBeInTheDocument();
  });

  it("收藏夹视图：批量移出", async () => {
    pathname = "/library/9";
    removeFrom.mockResolvedValue({ ok: true });
    const { user } = renderView("9");
    await selectBoth(user);
    await user.click(screen.getByRole("button", { name: "移出「组会」" }));
    await waitFor(() => expect(removeFrom).toHaveBeenCalledWith("9", ["501", "502"]));
    expect(toast.success).toHaveBeenCalledWith("已从「组会」移出 2 篇");
  });

  it("批量移除：确认后移除", async () => {
    remove.mockResolvedValue({ ok: true });
    const { user } = renderView();
    await selectBoth(user);
    await user.click(within(screen.getByRole("toolbar")).getByRole("button", { name: "移除" }));
    const dialog = await screen.findByRole("dialog", { name: "从文献库移除 2 篇？" });
    expect(
      within(dialog).getByText("这些文献的备注会删除，也会移出所有收藏夹。"),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "移除 2 篇" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(["501", "502"]));
    expect(toast.success).toHaveBeenCalledWith("已从文献库移除 2 篇");
  });

  it("falls back to previous page when page empties：这一页全部移除后回到上一页", async () => {
    remove.mockResolvedValue({ ok: true });
    const { user } = renderView("all", 3);
    await selectBoth(user);
    await user.click(within(screen.getByRole("toolbar")).getByRole("button", { name: "移除" }));
    await user.click(await screen.findByRole("button", { name: "移除 2 篇" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/library?page=2"));
  });

  it("批量导出：范围是选中的几篇", async () => {
    const { user } = renderView();
    await selectBoth(user);
    await user.click(within(screen.getByRole("toolbar")).getByRole("button", { name: "导出" }));
    const dialog = await screen.findByRole("dialog", { name: "导出" });
    expect(within(dialog).getByText("选中的 2 篇")).toBeInTheDocument();
  });
});
