import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MarkdownEditor } from "@/components/discussion/MarkdownEditor";
import { COMMENT_LIMITS } from "@/lib/discussion/limits";

function Harness({
  initial = "",
  onSubmit = vi.fn(),
  mentions = false,
}: {
  initial?: string;
  onSubmit?: () => void;
  mentions?: false | { subject: { type: "publication"; key: string } };
}) {
  const [value, setValue] = useState(initial);
  return (
    <MarkdownEditor
      value={value}
      onChange={setValue}
      label="评论内容"
      placeholder="写下你的看法…"
      limits={COMMENT_LIMITS}
      limitLabel="评论"
      mentions={mentions}
      onSubmitShortcut={onSubmit}
    />
  );
}

describe("MarkdownEditor", () => {
  it("编写 / 预览两个标签；预览用同一套渲染", async () => {
    const user = userEvent.setup();
    render(<Harness initial="**粗**" />);
    expect(screen.getByRole("tab", { name: "编写" })).toHaveAttribute("aria-selected", "true");
    await user.click(screen.getByRole("tab", { name: "预览" }));
    expect(screen.getByText("粗").tagName).toBe("STRONG");
    expect(screen.queryByRole("textbox")).toBeNull();
  });

  it("预览空内容", async () => {
    render(<Harness />);
    await userEvent.setup().click(screen.getByRole("tab", { name: "预览" }));
    expect(screen.getByText("没有可以预览的内容")).toBeInTheDocument();
  });

  it("计数：字素 / 上限，千分位", () => {
    render(<Harness initial={"字".repeat(186)} />);
    expect(screen.getByText("186 / 5,000")).toBeInTheDocument();
  });

  it("接近上限变琥珀色，超过时提示且标危险色", () => {
    const { unmount } = render(<Harness initial={"字".repeat(4000)} />);
    expect(screen.getByText("4,000 / 5,000")).toHaveClass("text-amber-700");
    unmount();
    render(<Harness initial={"字".repeat(5001)} />);
    expect(screen.getByText("5,001 / 5,000")).toHaveClass("text-status-danger");
    expect(screen.getByText("评论最多 5,000 字")).toBeInTheDocument();
  });

  it("字素不超、字节超", () => {
    render(<Harness initial={"👨‍👩‍👧".repeat(1200)} />);
    expect(screen.getByText("内容太长了，请删减一些")).toBeInTheDocument();
  });

  it("@ 超过 10 人", () => {
    const names = Array.from({ length: 11 }, (_, index) => `@user${index + 10}`).join(" ");
    render(<Harness initial={names} mentions={{ subject: { type: "publication", key: "1" } }} />);
    expect(screen.getByText("一条评论最多 @ 10 人")).toBeInTheDocument();
  });

  it("工具条按钮写全称；关掉 @ 时没有提及按钮", () => {
    render(<Harness />);
    for (const name of ["粗体", "斜体", "引用", "无序列表", "有序列表", "行内代码", "链接"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
    expect(screen.queryByRole("button", { name: "提及" })).toBeNull();
  });

  it("评论里有提及按钮", () => {
    render(<Harness mentions={{ subject: { type: "publication", key: "1" } }} />);
    expect(screen.getByRole("button", { name: "提及" })).toBeInTheDocument();
  });

  it("点粗体：没有选区插入成对标记", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "粗体" }));
    expect(screen.getByRole("textbox", { name: "评论内容" })).toHaveValue("****");
  });

  it("⌘Enter / Ctrl Enter 提交；输入法组字中不提交", () => {
    const onSubmit = vi.fn();
    render(<Harness initial="x" onSubmit={onSubmit} />);
    const box = screen.getByRole("textbox", { name: "评论内容" });
    fireEvent.keyDown(box, { key: "Enter", metaKey: true, isComposing: true });
    fireEvent.keyDown(box, { key: "Enter", metaKey: true, keyCode: 229 });
    expect(onSubmit).not.toHaveBeenCalled();
    fireEvent.keyDown(box, { key: "Enter", metaKey: true });
    fireEvent.keyDown(box, { key: "Enter", ctrlKey: true });
    expect(onSubmit).toHaveBeenCalledTimes(2);
  });
});
