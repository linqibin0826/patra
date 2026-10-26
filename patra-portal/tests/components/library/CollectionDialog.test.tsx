import { screen, waitFor } from "@testing-library/react";
import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { CollectionDialog } from "@/components/library/CollectionDialog";
import { DeleteCollectionDialog } from "@/components/library/DeleteCollectionDialog";
import {
  createCollection,
  deleteCollection,
  updateCollection,
} from "@/lib/interaction/collection-actions";

vi.mock("@/lib/interaction/collection-actions", () => ({
  createCollection: vi.fn(),
  updateCollection: vi.fn(),
  deleteCollection: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));
const push = vi.fn();
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push }),
}));

const create = vi.mocked(createCollection);
const update = vi.mocked(updateCollection);
const destroy = vi.mocked(deleteCollection);

beforeEach(() => {
  create.mockReset();
  update.mockReset();
  destroy.mockReset();
  push.mockReset();
  vi.mocked(toast.success).mockReset();
});

function renderCreate() {
  const onOpenChange = vi.fn();
  const view = renderWithLibrary(
    <CollectionDialog mode="create" open onOpenChange={onOpenChange} />,
    { overview: makeOverview() },
  );
  return { ...view, onOpenChange };
}

describe("新建 / 编辑收藏夹", () => {
  it("新建：标题、计数、说明", () => {
    renderCreate();
    expect(screen.getByRole("dialog", { name: "新建收藏夹" })).toBeInTheDocument();
    expect(screen.getByText("0 / 100")).toBeInTheDocument();
    expect(screen.getByText("0 / 500")).toBeInTheDocument();
    expect(screen.getByText("新建的收藏夹只有你能看到，之后可以设为公开。")).toBeInTheDocument();
  });

  it("名称为空：字段下提示，不提交", async () => {
    const { user } = renderCreate();
    await user.click(screen.getByRole("button", { name: "创建" }));
    expect(await screen.findByText("请输入名称")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });

  it("重名：字段下「已有同名收藏夹」", async () => {
    create.mockResolvedValue({ ok: false, reason: "name-taken" });
    const { user } = renderCreate();
    await user.type(screen.getByRole("textbox", { name: "名称" }), "组会");
    await user.click(screen.getByRole("button", { name: "创建" }));
    expect(await screen.findByText("已有同名收藏夹")).toBeInTheDocument();
  });

  it("保存失败：提示条", async () => {
    create.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = renderCreate();
    await user.type(screen.getByRole("textbox", { name: "名称" }), "组会");
    await user.click(screen.getByRole("button", { name: "创建" }));
    expect(await screen.findByText("没有保存成功，请稍后再试")).toBeInTheDocument();
  });

  it("新建成功：进入新收藏夹，toast", async () => {
    create.mockResolvedValue({
      ok: true,
      collection: makeCollection({ collectionId: "77", name: "组会" }),
    });
    const { user } = renderCreate();
    await user.type(screen.getByRole("textbox", { name: "名称" }), "组会");
    await user.click(screen.getByRole("button", { name: "创建" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/library/77"));
    expect(toast.success).toHaveBeenCalledWith("已创建收藏夹「组会」");
    expect(create).toHaveBeenCalledWith({ name: "组会", description: "" });
  });

  it("编辑：带出原值，成功后关闭并 toast", async () => {
    const collection = makeCollection();
    update.mockResolvedValue({ ok: true, collection: { ...collection, name: "组会 2" } });
    const onOpenChange = vi.fn();
    const { user } = renderWithLibrary(
      <CollectionDialog mode="edit" collection={collection} open onOpenChange={onOpenChange} />,
      { overview: makeOverview() },
    );
    expect(screen.getByRole("dialog", { name: "编辑收藏夹" })).toBeInTheDocument();
    const name = screen.getByRole("textbox", { name: "名称" });
    expect(name).toHaveValue("组会");
    await user.type(name, " 2");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(toast.success).toHaveBeenCalledWith("收藏夹已保存");
  });
});

describe("删除收藏夹", () => {
  it("私有的：标题与正文；删除后回文献库", async () => {
    destroy.mockResolvedValue({ ok: true });
    const { user } = renderWithLibrary(
      <DeleteCollectionDialog collection={makeCollection()} open onOpenChange={() => {}} />,
      { overview: makeOverview() },
    );
    expect(screen.getByRole("dialog", { name: "删除收藏夹「组会」？" })).toBeInTheDocument();
    expect(
      screen.getByText("里面的 23 篇文献不会从文献库删除；不在其他收藏夹里的，会回到「未分类」。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/公开链接会失效/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "删除收藏夹" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/library"));
    expect(toast.success).toHaveBeenCalledWith("已删除收藏夹「组会」");
  });

  it("公开的：多一句；失败提示", async () => {
    destroy.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = renderWithLibrary(
      <DeleteCollectionDialog
        collection={makeCollection({ visibility: "PUBLIC" })}
        open
        onOpenChange={() => {}}
      />,
      { overview: makeOverview() },
    );
    expect(screen.getByText("公开链接会失效，你的主页上也不再显示它。")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "删除收藏夹" }));
    expect(await screen.findByText("没有删除成功，请稍后再试")).toBeInTheDocument();
  });
});

describe("CollectionDialog · 评审修复", () => {
  it("概览重取换了收藏夹对象（同一个夹）时不清掉正在输入的内容", async () => {
    const collection = makeCollection();
    const { user, rerender } = renderWithLibrary(
      <CollectionDialog mode="edit" collection={collection} open onOpenChange={() => {}} />,
      { overview: makeOverview() },
    );
    const name = screen.getByRole("textbox", { name: "名称" });
    await user.clear(name);
    await user.type(name, "新名字");
    rerender(
      <CollectionDialog mode="edit" collection={{ ...collection }} open onOpenChange={() => {}} />,
    );
    expect(screen.getByRole("textbox", { name: "名称" })).toHaveValue("新名字");
  });
});
