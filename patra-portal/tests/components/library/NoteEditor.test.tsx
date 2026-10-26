import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeLibraryItem } from "@tests/fixtures/interaction";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NoteEditor } from "@/components/library/NoteEditor";
import { saveNote } from "@/lib/interaction/library-actions";

vi.mock("@/lib/interaction/library-actions", () => ({ saveNote: vi.fn() }));
vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  useRouter: () => ({ push: vi.fn() }),
}));

const save = vi.mocked(saveNote);

beforeEach(() => {
  save.mockReset();
});

function setup(note: string | null = null) {
  const onSaved = vi.fn();
  render(<NoteEditor itemId="501" note={note} onSaved={onSaved} />);
  return { onSaved, user: userEvent.setup() };
}

describe("NoteEditor", () => {
  it("没有备注：「+ 添加备注」进入编辑态", async () => {
    const { user } = setup();
    await user.click(screen.getByRole("button", { name: "+ 添加备注" }));
    expect(screen.getByRole("textbox", { name: "备注" })).toHaveFocus();
    expect(screen.getByText("0 / 500")).toBeInTheDocument();
  });

  it("有备注：展示正文与「编辑」", () => {
    setup("第 3 张表");
    expect(screen.getByText("第 3 张表")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "编辑" })).toBeInTheDocument();
  });

  it("按字素计数，emoji 计 1", async () => {
    const { user } = setup();
    await user.click(screen.getByRole("button", { name: "+ 添加备注" }));
    await user.type(screen.getByRole("textbox", { name: "备注" }), "好👍🏽");
    expect(screen.getByText("2 / 500")).toBeInTheDocument();
  });

  it("超过 500 字：提示并禁用保存", async () => {
    const { user } = setup();
    await user.click(screen.getByRole("button", { name: "+ 添加备注" }));
    await user.click(screen.getByRole("textbox", { name: "备注" }));
    await user.paste("字".repeat(501));
    expect(screen.getByText("备注最多 500 字")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "保存" })).toBeDisabled();
  });

  it("保存中「保存中…」，成功回到展示态并回调", async () => {
    const item = makeLibraryItem({ note: "组会讲" });
    let answer: (value: Awaited<ReturnType<typeof saveNote>>) => void = () => {};
    save.mockReturnValue(new Promise((resolve) => (answer = resolve)));
    const { user, onSaved } = setup();
    await user.click(screen.getByRole("button", { name: "+ 添加备注" }));
    await user.type(screen.getByRole("textbox", { name: "备注" }), "组会讲");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByRole("button", { name: "保存中…" })).toBeInTheDocument();
    answer({ ok: true, item });
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(item));
    expect(save).toHaveBeenCalledWith("501", "组会讲");
  });

  it("保存失败：提示并保留输入", async () => {
    save.mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = setup();
    await user.click(screen.getByRole("button", { name: "+ 添加备注" }));
    await user.type(screen.getByRole("textbox", { name: "备注" }), "组会讲");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByText("备注没有保存成功，请稍后再试")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "备注" })).toHaveValue("组会讲");
  });

  it("清空后保存 = 删除备注", async () => {
    save.mockResolvedValue({ ok: true, item: makeLibraryItem() });
    const { user } = setup("旧备注");
    await user.click(screen.getByRole("button", { name: "编辑" }));
    await user.clear(screen.getByRole("textbox", { name: "备注" }));
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(save).toHaveBeenCalledWith("501", ""));
  });

  it("取消：回到展示态，不保存", async () => {
    const { user } = setup("旧备注");
    await user.click(screen.getByRole("button", { name: "编辑" }));
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(screen.getByText("旧备注")).toBeInTheDocument();
    expect(save).not.toHaveBeenCalled();
  });
});
