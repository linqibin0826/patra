import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Dialog } from "@/components/ui/dialog";

/// 两个独立的模态对话框：先开「注销」，再在它上面开「再认证」（spec §6.2、§14 第 1 条）
function Harness() {
  const [outer, setOuter] = useState(true);
  const [inner, setInner] = useState(false);
  return (
    <>
      <Dialog open={outer} onOpenChange={setOuter} title="注销账号">
        <button type="button" onClick={() => setInner(true)}>
          打开再认证
        </button>
      </Dialog>
      <Dialog open={inner} onOpenChange={setInner} title="确认是你本人" size="sm">
        <input aria-label="密码" />
      </Dialog>
    </>
  );
}

describe("两个独立的模态叠放", () => {
  it("上层能聚焦和输入；Esc 只关上层，下层还在", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(await screen.findByRole("button", { name: "打开再认证" }));
    const input = await screen.findByLabelText("密码");
    await waitFor(() => expect(input).toHaveFocus());
    await user.type(input, "abc");
    expect(input).toHaveValue("abc");
    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "确认是你本人" })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole("dialog", { name: "注销账号" })).toBeInTheDocument();
  });
});
