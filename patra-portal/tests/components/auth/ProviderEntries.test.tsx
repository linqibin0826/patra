import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProviderEntries } from "@/components/auth/ProviderEntries";

describe("ProviderEntries", () => {
  it("登录页：三个链接，指向发起路由，带发起页和 next", () => {
    render(<ProviderEntries page="login" next="/papers?q=GLP-1" />);
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual([
      "用 Google 账号登录",
      "用 GitHub 账号登录",
      "用 ORCID 账号登录",
    ]);
    expect(screen.getByRole("link", { name: "用 GitHub 账号登录" })).toHaveAttribute(
      "href",
      "/api/auth/oauth/github/start?from=login&next=%2Fpapers%3Fq%3DGLP-1",
    );
    expect(screen.getByText("或用邮箱登录")).toBeInTheDocument();
  });

  it("注册页：文案和发起页跟着变", () => {
    render(<ProviderEntries page="register" next="/" />);
    expect(screen.getByRole("link", { name: "用 ORCID 账号注册" })).toHaveAttribute(
      "href",
      "/api/auth/oauth/orcid/start?from=register&next=%2F",
    );
    expect(screen.getByText("或用邮箱注册")).toBeInTheDocument();
  });

  it("点了一个：它显示正在前往，三个都不可点，再点不会再走", () => {
    render(<ProviderEntries page="login" next="/" />);
    const github = screen.getByRole("link", { name: "用 GitHub 账号登录" });
    fireEvent.click(github);
    expect(github).toHaveTextContent("正在前往 GitHub…");
    for (const link of screen.getAllByRole("link")) {
      expect(link).toHaveAttribute("aria-disabled", "true");
    }
    const again = fireEvent.click(screen.getByRole("link", { name: "用 Google 账号登录" }));
    expect(again).toBe(false);
    // Google 的 G 不能改色：禁用时只淡文字，标识不变淡
    for (const mark of document.querySelectorAll("img")) {
      expect(mark.className).not.toContain("opacity");
    }
  });

  it("从第三方按后退回到往返缓存的页面：按钮复位", () => {
    render(<ProviderEntries page="login" next="/" />);
    fireEvent.click(screen.getByRole("link", { name: "用 GitHub 账号登录" }));
    act(() => {
      window.dispatchEvent(new Event("pageshow"));
    });
    for (const link of screen.getAllByRole("link")) {
      expect(link).not.toHaveAttribute("aria-disabled");
    }
    expect(screen.getByRole("link", { name: "用 GitHub 账号登录" })).toBeInTheDocument();
  });
});
