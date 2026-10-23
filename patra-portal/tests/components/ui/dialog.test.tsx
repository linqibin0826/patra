import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Dialog } from "@/components/ui/dialog";

function Harness({ dismissible = true }: { dismissible?: boolean }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        打开
      </button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="调整头像"
        description="说明"
        dismissible={dismissible}
      >
        <input aria-label="第一个输入框" />
      </Dialog>
    </>
  );
}

describe("Dialog", () => {
  it("打开后是带标题的模态对话框，焦点在第一个输入框", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "打开" }));
    const dialog = await screen.findByRole("dialog", { name: "调整头像" });
    expect(dialog).toHaveAccessibleDescription("说明");
    await waitFor(() => expect(screen.getByLabelText("第一个输入框")).toHaveFocus());
  });

  it("Esc 关闭，焦点回到触发按钮", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: "打开" });
    await user.click(trigger);
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });

  it("dismissible 为 false（提交中）：Esc 不关", async () => {
    const user = userEvent.setup();
    render(<Harness dismissible={false} />);
    await user.click(screen.getByRole("button", { name: "打开" }));
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
