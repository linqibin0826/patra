import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AuthShell } from "@/components/auth/AuthShell";

function renderShell() {
  return render(
    <AuthShell title="登录" description="用邮箱和密码登录 Patra" back="/papers?q=GLP-1">
      <form aria-label="登录表单" />
    </AuthShell>,
  );
}

describe("AuthShell", () => {
  it("标题、副文案和表单在主区域里", () => {
    renderShell();
    const main = screen.getByRole("main");
    expect(within(main).getByRole("heading", { level: 1, name: "登录" })).toBeInTheDocument();
    expect(within(main).getByText("用邮箱和密码登录 Patra")).toBeInTheDocument();
    expect(within(main).getByRole("form", { name: "登录表单" })).toBeInTheDocument();
  });

  it("左上角的品牌标识回首页", () => {
    renderShell();
    expect(screen.getByRole("link", { name: "Patra 首页" })).toHaveAttribute("href", "/");
  });

  it("「返回」指向 next", () => {
    renderShell();
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute("href", "/papers?q=GLP-1");
  });
});
