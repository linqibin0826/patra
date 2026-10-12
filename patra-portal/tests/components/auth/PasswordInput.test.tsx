import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PasswordInput } from "@/components/auth/PasswordInput";

function renderPassword() {
  render(
    <>
      <label htmlFor="pw">密码</label>
      <PasswordInput id="pw" autoComplete="current-password" aria-describedby="pw-hint" />
    </>,
  );
  return screen.getByLabelText("密码") as HTMLInputElement;
}

describe("PasswordInput", () => {
  it("默认隐藏；切换按钮的读屏名称随状态变化", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    expect(input).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "隐藏密码" }));
    expect(input).toHaveAttribute("type", "password");
  });

  it("其余属性原样交给输入框", () => {
    const input = renderPassword();
    expect(input).toHaveAttribute("autocomplete", "current-password");
    expect(input).toHaveAttribute("aria-describedby", "pw-hint");
  });

  it("用鼠标切换时焦点留在输入框，光标位置和选区不变", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    await user.type(input, "secret-42");
    input.setSelectionRange(2, 5);
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveFocus();
    expect([input.selectionStart, input.selectionEnd]).toEqual([2, 5]);
  });

  it("浏览器改 type 时把光标移到了开头，也恢复成切换前的选区", async () => {
    const user = userEvent.setup();
    const input = renderPassword();
    await user.type(input, "secret-42");
    input.setSelectionRange(3, 6);
    // 有的浏览器在 type 改变时重置选区：照常改 type，再把光标放到开头
    const setType = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "type")?.set;
    vi.spyOn(input, "type", "set").mockImplementation(function (this: HTMLInputElement, value) {
      setType?.call(this, value);
      this.setSelectionRange(0, 0);
    });
    await user.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveAttribute("type", "text");
    expect([input.selectionStart, input.selectionEnd]).toEqual([3, 6]);
  });

  it("切换按钮不是提交按钮", () => {
    renderPassword();
    expect(screen.getByRole("button", { name: "显示密码" })).toHaveAttribute("type", "button");
  });
});
