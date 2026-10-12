import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { ReactNode } from "react";
import { FormField } from "@/components/auth/FormField";

function renderField(props: { hint?: ReactNode; error?: ReactNode }) {
  return render(
    <FormField id="login-email" label="邮箱" {...props}>
      {(control) => <input {...control} type="email" />}
    </FormField>,
  );
}

describe("FormField", () => {
  it("标签和输入框关联", () => {
    renderField({});
    expect(screen.getByLabelText("邮箱")).toHaveAttribute("id", "login-email");
  });

  it("没有错误时显示常驻提示，输入框的描述指向它", () => {
    renderField({ hint: "8 ~ 64 位，区分大小写" });
    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveAttribute("aria-invalid", "false");
    expect(input).toHaveAccessibleDescription("8 ~ 64 位，区分大小写");
  });

  it("有错误时错误文案替换常驻提示，带图标，输入框标为无效", () => {
    renderField({ hint: "8 ~ 64 位，区分大小写", error: "邮箱格式不正确" });
    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("邮箱格式不正确");
    expect(screen.queryByText("8 ~ 64 位，区分大小写")).not.toBeInTheDocument();
    const line = screen.getByText("邮箱格式不正确").closest("p");
    expect(line?.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("错误替换提示时，错误行是新插入的节点（入场过渡靠插入触发）", () => {
    const field = (error?: string) => (
      <FormField id="register-password" label="密码" hint="至少 8 位" error={error}>
        {(control) => <input {...control} type="password" />}
      </FormField>
    );
    const { rerender } = render(field());
    const hint = screen.getByText("至少 8 位");
    rerender(field("密码至少 8 位"));
    expect(screen.getByText("密码至少 8 位").closest("p")).not.toBe(hint);
  });

  it("错误文案里可以带链接", () => {
    renderField({
      error: (
        <>
          该邮箱已注册，<a href="/login">去登录</a>
        </>
      ),
    });
    expect(screen.getByLabelText("邮箱")).toHaveAccessibleDescription("该邮箱已注册，去登录");
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute("href", "/login");
  });

  it("既没有提示也没有错误时不带描述", () => {
    renderField({});
    expect(screen.getByLabelText("邮箱")).not.toHaveAttribute("aria-describedby");
  });
});
