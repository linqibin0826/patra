import { screen, waitFor } from "@testing-library/react";
import { makeCollection, makeOverview } from "@tests/fixtures/interaction";
import { renderWithLibrary } from "@tests/support/library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CollectionChecklist } from "@/components/library/CollectionChecklist";
import {
  addToCollection,
  createCollection,
  removeFromCollection,
} from "@/lib/interaction/collection-actions";

vi.mock("@/lib/interaction/collection-actions", () => ({
  addToCollection: vi.fn(),
  removeFromCollection: vi.fn(),
  createCollection: vi.fn(),
}));
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push: vi.fn() }),
}));

const add = vi.mocked(addToCollection);
const removeFrom = vi.mocked(removeFromCollection);
const create = vi.mocked(createCollection);
const OK_ADD = {
  ok: true,
  result: { added: 1, alreadyIn: 0, skippedFull: 0, skippedNotInLibrary: 0 },
} as const;

beforeEach(() => {
  add.mockReset();
  removeFrom.mockReset();
  create.mockReset();
});

const collections = (n: number) =>
  Array.from({ length: n }, (_, i) =>
    makeCollection({ collectionId: String(i + 1), name: `夹 ${i + 1}` }),
  );

function renderList(options: {
  overview?: ReturnType<typeof makeOverview>;
  memberOf?: (id: string) => number;
  itemIds?: string[];
  onChanged?: () => void;
}) {
  return renderWithLibrary(
    <CollectionChecklist
      itemIds={options.itemIds ?? ["501"]}
      memberOf={options.memberOf ?? (() => 0)}
      onChanged={options.onChanged ?? (() => {})}
    />,
    { overview: options.overview ?? makeOverview({ collections: collections(2) }) },
  );
}

describe("CollectionChecklist", () => {
  it("一个收藏夹都没有：提示新建", () => {
    renderList({ overview: makeOverview({ collections: [] }) });
    expect(screen.getByText("还没有收藏夹，新建一个来整理文献。")).toBeInTheDocument();
  });

  it("勾上立即放进收藏夹，成功后回调", async () => {
    add.mockResolvedValue(OK_ADD);
    const onChanged = vi.fn();
    const { user } = renderList({ onChanged });
    await user.click(screen.getByRole("checkbox", { name: /夹 1/ }));
    expect(screen.getByRole("checkbox", { name: /夹 1/ })).toBeChecked();
    await waitFor(() => expect(add).toHaveBeenCalledWith("1", ["501"]));
    await waitFor(() => expect(onChanged).toHaveBeenCalledWith("1", true, OK_ADD.result));
  });

  it("取消勾选移出收藏夹", async () => {
    removeFrom.mockResolvedValue({ ok: true });
    const { user } = renderList({ memberOf: (id) => (id === "1" ? 1 : 0) });
    await user.click(screen.getByRole("checkbox", { name: /夹 1/ }));
    await waitFor(() => expect(removeFrom).toHaveBeenCalledWith("1", ["501"]));
  });

  it("失败：复选框回原值，行下「没有保存成功」", async () => {
    add.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = renderList({});
    await user.click(screen.getByRole("checkbox", { name: /夹 1/ }));
    expect(await screen.findByText("没有保存成功")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /夹 1/ })).not.toBeChecked();
  });

  it("勾选时收藏夹刚满：回滚并提示「这个收藏夹已满 5,000 篇」", async () => {
    add.mockResolvedValue({ ok: true, result: { ...OK_ADD.result, added: 0, skippedFull: 1 } });
    const { user } = renderList({});
    await user.click(screen.getByRole("checkbox", { name: /夹 1/ }));
    expect(await screen.findByText("这个收藏夹已满 5,000 篇")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: /夹 1/ })).not.toBeChecked();
  });

  it("已满 5,000 篇且这篇不在里面：禁用并标「已满」", () => {
    renderList({
      overview: makeOverview({
        collections: [makeCollection({ collectionId: "1", name: "满夹", itemCount: 5000 })],
      }),
    });
    expect(screen.getByRole("checkbox", { name: /满夹/ })).toBeDisabled();
    expect(screen.getByText("已满")).toBeInTheDocument();
  });

  it("9 个以上出现筛选框，只在本地过滤", async () => {
    const { user } = renderList({ overview: makeOverview({ collections: collections(9) }) });
    await user.type(screen.getByRole("searchbox", { name: "筛选收藏夹" }), "夹 9");
    expect(screen.getAllByRole("checkbox")).toHaveLength(1);
    await user.clear(screen.getByRole("searchbox", { name: "筛选收藏夹" }));
    await user.type(screen.getByRole("searchbox", { name: "筛选收藏夹" }), "没有");
    expect(screen.getByText("没有匹配的收藏夹")).toBeInTheDocument();
  });

  it("可见性图标有读屏文字", () => {
    renderList({
      overview: makeOverview({
        collections: [makeCollection({ collectionId: "1", name: "公开夹", visibility: "PUBLIC" })],
      }),
    });
    expect(screen.getByRole("checkbox", { name: "公开夹 公开" })).toBeInTheDocument();
  });

  describe("行内新建", () => {
    it("名称为空：请输入名称", async () => {
      const { user } = renderList({});
      await user.click(screen.getByRole("button", { name: "+ 新建收藏夹" }));
      expect(screen.getByRole("textbox", { name: "收藏夹名称" })).toHaveFocus();
      await user.click(screen.getByRole("button", { name: "创建" }));
      expect(screen.getByText("请输入名称")).toBeInTheDocument();
      expect(create).not.toHaveBeenCalled();
    });

    it("超过 100 字：名称最多 100 个字", async () => {
      const { user } = renderList({});
      await user.click(screen.getByRole("button", { name: "+ 新建收藏夹" }));
      await user.click(screen.getByRole("textbox", { name: "收藏夹名称" }));
      await user.paste("名".repeat(101));
      await user.click(screen.getByRole("button", { name: "创建" }));
      expect(screen.getByText("名称最多 100 个字")).toBeInTheDocument();
    });

    it.each([
      ["name-taken", "已有同名收藏夹"],
      ["unavailable", "没有创建成功，请稍后再试"],
    ] as const)("%s：%s", async (reason, text) => {
      create.mockResolvedValue({ ok: false, reason });
      const { user } = renderList({});
      await user.click(screen.getByRole("button", { name: "+ 新建收藏夹" }));
      await user.type(screen.getByRole("textbox", { name: "收藏夹名称" }), "组会{Enter}");
      expect(await screen.findByText(text)).toBeInTheDocument();
    });

    it("创建成功：新夹排第一行并放进去", async () => {
      create.mockResolvedValue({
        ok: true,
        collection: makeCollection({ collectionId: "77", name: "新夹" }),
      });
      add.mockResolvedValue(OK_ADD);
      const { user } = renderList({});
      await user.click(screen.getByRole("button", { name: "+ 新建收藏夹" }));
      await user.type(screen.getByRole("textbox", { name: "收藏夹名称" }), "新夹{Enter}");
      await waitFor(() => expect(add).toHaveBeenCalledWith("77", ["501"]));
      const boxes = screen.getAllByRole("checkbox");
      expect(boxes[0]).toHaveAccessibleName(/新夹/);
      expect(boxes[0]).toBeChecked();
    });

    it("已有 200 个：新建禁用", () => {
      renderList({ overview: makeOverview({ collections: collections(200) }) });
      expect(screen.getByRole("button", { name: "+ 新建收藏夹" })).toHaveAttribute(
        "aria-disabled",
        "true",
      );
      expect(screen.getByText("最多 200 个收藏夹")).toBeInTheDocument();
    });
  });

  it("三态：只有一部分条目在夹里时是半选", () => {
    renderList({ itemIds: ["501", "502"], memberOf: (id) => (id === "1" ? 1 : 0) });
    expect((screen.getByRole("checkbox", { name: /夹 1/ }) as HTMLInputElement).indeterminate).toBe(
      true,
    );
  });
});
