import { render, screen } from "@testing-library/react";
import { Mail } from "lucide-react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FormAlert } from "@/components/auth/FormAlert";

describe("FormAlert", () => {
  it("是 alert，读屏插入时就读出；带图标，不只靠颜色", () => {
    render(<FormAlert>邮箱或密码错误</FormAlert>);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("邮箱或密码错误");
    expect(alert.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });
});

describe("FormAlert 的语气", () => {
  it.each(["success", "info"] as const)("%s 是 status，不打断朗读", (tone) => {
    render(<FormAlert tone={tone}>密码已重设，请用新密码登录</FormAlert>);
    expect(screen.getByRole("status")).toHaveTextContent("密码已重设，请用新密码登录");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("显式 danger 仍是 alert", () => {
    render(<FormAlert tone="danger">没有成功</FormAlert>);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});

describe("FormAlert 的警示语气、操作与图标", () => {
  it("warning 是 status；action 显示在右端", () => {
    render(
      <FormAlert tone="warning" action={<button type="button">重试</button>}>
        删除后资料无法恢复
      </FormAlert>,
    );
    const alert = screen.getByRole("status");
    expect(alert).toHaveTextContent("删除后资料无法恢复");
    expect(alert).toContainElement(screen.getByRole("button", { name: "重试" }));
  });

  it("可以换图标", () => {
    render(
      <FormAlert tone="info" icon={Mail}>
        正在等待确认
      </FormAlert>,
    );
    expect(screen.getByRole("status").querySelector("svg.lucide-mail")).not.toBeNull();
  });
});
