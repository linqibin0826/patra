import { screen, waitFor } from "@testing-library/react";
import {
  makeCollection,
  makeItemStatus,
  makeLibraryItem,
  makeOverview,
} from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { SaveButton } from "@/components/library/SaveButton";
import { collectPublication, removeItems } from "@/lib/interaction/library-actions";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/interaction/library-actions", () => ({
  collectPublication: vi.fn(),
  removeItems: vi.fn(),
  saveNote: vi.fn(),
}));
vi.mock("@/lib/interaction/collection-actions", () => ({
  addToCollection: vi.fn(),
  removeFromCollection: vi.fn(),
  createCollection: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push: vi.fn() }),
}));

const collect = vi.mocked(collectPublication);
const remove = vi.mocked(removeItems);
const success = vi.mocked(toast.success);

beforeEach(() => {
  collect.mockReset();
  remove.mockReset();
  success.mockReset();
});

const SAVED = makeItemStatus({
  key: "11",
  collected: true,
  itemId: "501",
  collectedAt: "2026-10-24T02:00:00Z",
});
const TWO = makeOverview({
  collections: [
    makeCollection({ collectionId: "9", name: "组会" }),
    makeCollection({ collectionId: "10", name: "心衰综述", visibility: "PUBLIC" }),
  ],
});

async function openSaved(status = SAVED) {
  const view = renderWithLibrary(<SaveButton paperKey="11" title="T" variant="icon" />, {
    statuses: [status],
    overview: TWO,
  });
  await view.user.click(screen.getByRole("button", { name: "已收藏，管理收藏" }));
  return view;
}

describe("SavePopover", () => {
  it("收藏进行中：头部「正在收藏…」", async () => {
    collect.mockReturnValue(new Promise(() => {}));
    const { user } = renderWithLibrary(<SaveButton paperKey="11" title="T" variant="icon" />, {
      statuses: [makeItemStatus({ key: "11" })],
      overview: TWO,
    });
    await user.click(screen.getByRole("button", { name: "收藏「T」" }));
    expect(await screen.findByText("正在收藏…")).toBeInTheDocument();
  });

  it("头部右端「查看文献库 →」链到 /library", async () => {
    await openSaved();
    expect(await screen.findByRole("link", { name: "查看文献库 →" })).toHaveAttribute(
      "href",
      "/library",
    );
  });

  it("勾选状态来自我的状态里的收藏夹", async () => {
    await openSaved({ ...SAVED, collectionIds: ["10"] });
    expect(await screen.findByRole("checkbox", { name: /组会/ })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: /心衰综述/ })).toBeChecked();
  });

  it("没有备注、不在收藏夹里：立即移除，toast 带撤销，点撤销重新收藏", async () => {
    remove.mockResolvedValue({ ok: true });
    collect.mockResolvedValue({ ok: true, created: true, item: makeLibraryItem() });
    const { user, client } = await openSaved();
    await user.click(await screen.findByRole("button", { name: "从文献库移除" }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(["501"]));
    expect(success).toHaveBeenCalledWith(
      "已从文献库移除",
      expect.objectContaining({ action: expect.objectContaining({ label: "撤销" }) }),
    );
    expect(client.getQueryData(queryKeys.libraryStatus(["11"]))).toMatchObject([
      { key: "11", collected: false },
    ]);
    const options = success.mock.calls[0]?.[1] as unknown as { action: { onClick: () => void } };
    options.action.onClick();
    await waitFor(() => expect(collect).toHaveBeenCalledWith("11"));
  });

  it("有备注且在 2 个夹里：先确认，确认后 toast 不带撤销", async () => {
    remove.mockResolvedValue({ ok: true });
    collect.mockResolvedValue({
      ok: true,
      created: false,
      item: makeLibraryItem({ note: "第 3 张表", collectionIds: ["9", "10"] }),
    });
    const { user } = await openSaved({ ...SAVED, hasNote: true, collectionIds: ["9", "10"] });
    await user.click(await screen.findByRole("button", { name: "从文献库移除" }));
    expect(screen.getByText("移除后，这篇的备注会删除，也会移出 2 个收藏夹。")).toBeInTheDocument();
    expect(remove).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "移除" }));
    await waitFor(() => expect(success).toHaveBeenCalledWith("已从文献库移除"));
  });

  it.each([
    [{ hasNote: true, collectionIds: [] }, "移除后，这篇的备注会删除。"],
    [{ hasNote: false, collectionIds: ["9", "10"] }, "移除后，这篇会移出 2 个收藏夹。"],
  ])("确认文案 %o", async (patch, text) => {
    collect.mockResolvedValue({ ok: true, created: false, item: makeLibraryItem({ note: "x" }) });
    const { user } = await openSaved({ ...SAVED, ...patch });
    await user.click(await screen.findByRole("button", { name: "从文献库移除" }));
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it("移除失败：「没有移除成功，请稍后再试」", async () => {
    remove.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = await openSaved();
    await user.click(await screen.findByRole("button", { name: "从文献库移除" }));
    expect(await screen.findByText("没有移除成功，请稍后再试")).toBeInTheDocument();
  });

  it("有备注：取回备注正文显示在浮层里", async () => {
    collect.mockResolvedValue({
      ok: true,
      created: false,
      item: makeLibraryItem({ note: "第 3 张表的亚组结果组会讲" }),
    });
    await openSaved({ ...SAVED, hasNote: true });
    expect(await screen.findByText("第 3 张表的亚组结果组会讲")).toBeInTheDocument();
  });
});
