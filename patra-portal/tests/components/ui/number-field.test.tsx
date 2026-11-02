import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NumberField } from "@/components/ui/number-field";

function renderField(props: { value: number | null; disabled?: boolean }) {
  const onValueChange = vi.fn();
  render(
    <>
      <label htmlFor="rounds">审稿轮数</label>
      <NumberField
        id="rounds"
        value={props.value}
        onValueChange={onValueChange}
        min={1}
        max={10}
        disabled={props.disabled}
      />
    </>,
  );
  return { onValueChange, input: screen.getByRole("textbox", { name: "审稿轮数" }) };
}

describe("NumberField", () => {
  it("显示当前值，点「加一」回调加一后的值", async () => {
    const user = userEvent.setup();
    const { input, onValueChange } = renderField({ value: 2 });
    expect(input).toHaveValue("2");
    await user.click(screen.getByRole("button", { name: "加一" }));
    expect(onValueChange).toHaveBeenLastCalledWith(3);
  });

  it("到上下限的按钮禁用", () => {
    renderField({ value: 10 });
    expect(screen.getByRole("button", { name: "加一" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "减一" })).toBeEnabled();
  });

  it("下限时「减一」禁用", () => {
    renderField({ value: 1 });
    expect(screen.getByRole("button", { name: "减一" })).toBeDisabled();
  });

  it("直接敲超出范围的数不夹取", async () => {
    const user = userEvent.setup();
    const { input, onValueChange } = renderField({ value: null });
    await user.type(input, "11");
    fireEvent.blur(input);
    expect(onValueChange).toHaveBeenLastCalledWith(11);
  });

  it("禁用时输入框和两个按钮都禁用", () => {
    const { input } = renderField({ value: 1, disabled: true });
    expect(input).toBeDisabled();
    expect(screen.getByRole("button", { name: "加一" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "减一" })).toBeDisabled();
  });
});
