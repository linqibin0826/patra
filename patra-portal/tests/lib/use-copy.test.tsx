import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CopyButton } from "@/components/library/CopyButton";

const writeText = vi.fn<(text: string) => Promise<void>>();
const execCommand = vi.fn<(command: string) => boolean>();

/// user-event 的 `setup()` 会换掉 `navigator.clipboard`：先建好 user 再装桩
function userWithClipboard(options?: Parameters<typeof userEvent.setup>[0]) {
  const user = userEvent.setup(options);
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
  return user;
}

beforeEach(() => {
  Object.defineProperty(document, "execCommand", { value: execCommand, configurable: true });
});

afterEach(() => {
  writeText.mockReset();
  execCommand.mockReset();
  vi.useRealTimers();
});

function setup() {
  render(
    <>
      <pre data-testid="target">[1] Chen Y. Title.</pre>
      <CopyButton
        label="复制引用"
        text="[1] Chen Y. Title."
        target={() => document.querySelector("[data-testid=target]")}
      />
    </>,
  );
}

describe("复制与剪贴板降级", () => {
  it("剪贴板接口可用：按钮内「已复制」，1.6 秒后恢复", async () => {
    writeText.mockResolvedValue();
    setup();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userWithClipboard({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "复制引用" }));
    expect(writeText).toHaveBeenCalledWith("[1] Chen Y. Title.");
    expect(screen.getByRole("button", { name: "已复制" })).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1600);
    });
    expect(screen.getByRole("button", { name: "复制引用" })).toBeInTheDocument();
  });

  it("剪贴板接口不可用：选中文字后 execCommand 复制", async () => {
    writeText.mockRejectedValue(new Error("insecure"));
    execCommand.mockReturnValue(true);
    setup();
    await userWithClipboard().click(screen.getByRole("button", { name: "复制引用" }));
    expect(execCommand).toHaveBeenCalledWith("copy");
    expect(window.getSelection()?.toString()).toBe("[1] Chen Y. Title.");
    expect(screen.getByRole("button", { name: "已复制" })).toBeInTheDocument();
  });

  it("都失败：保持选中，提示按快捷键复制，读屏播报", async () => {
    writeText.mockRejectedValue(new Error("insecure"));
    execCommand.mockReturnValue(false);
    vi.spyOn(navigator, "platform", "get").mockReturnValue("MacIntel");
    setup();
    await userWithClipboard().click(screen.getByRole("button", { name: "复制引用" }));
    const hint = screen.getByText("已选中，按 ⌘C 复制");
    expect(hint).toHaveAttribute("aria-live", "polite");
    expect(window.getSelection()?.toString()).toBe("[1] Chen Y. Title.");
  });

  it("非 Mac：提示 Ctrl+C", async () => {
    writeText.mockRejectedValue(new Error("insecure"));
    execCommand.mockReturnValue(false);
    vi.spyOn(navigator, "platform", "get").mockReturnValue("Win32");
    setup();
    await userWithClipboard().click(screen.getByRole("button", { name: "复制引用" }));
    expect(screen.getByText("已选中，按 Ctrl+C 复制")).toBeInTheDocument();
  });
});
