import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ResponsivePopover } from "@/components/ui/popover";

afterEach(() => {
  vi.unstubAllGlobals();
});

function Harness() {
  const [open, setOpen] = useState(false);
  const anchor = useRef<HTMLButtonElement>(null);
  return (
    <>
      <button ref={anchor} type="button" onClick={() => setOpen(true)}>
        收藏
      </button>
      <ResponsivePopover open={open} onOpenChange={setOpen} anchor={anchor} title="收藏">
        <button type="button">第一项</button>
      </ResponsivePopover>
    </>
  );
}

/// 让 `matchMedia` 命中窄屏
function stubNarrow(narrow: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: narrow,
      addEventListener: () => {},
      removeEventListener: () => {},
    })),
  );
}

describe("ResponsivePopover", () => {
  it("桌面：锚在按钮上的浮层，Esc 关闭后焦点回到按钮", async () => {
    stubNarrow(false);
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "收藏" }));
    const popup = await screen.findByRole("dialog", { name: "收藏" });
    expect(popup).toHaveAttribute("data-variant", "popover");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole("button", { name: "收藏" })).toHaveFocus());
  });

  it("≤ 880px：底部抽屉", async () => {
    stubNarrow(true);
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "收藏" }));
    expect(await screen.findByRole("dialog", { name: "收藏" })).toHaveAttribute(
      "data-variant",
      "sheet",
    );
  });
});
