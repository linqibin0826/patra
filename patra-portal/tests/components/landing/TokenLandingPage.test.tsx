import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { type LandingOutcome, TokenLandingPage } from "@/components/landing/TokenLandingPage";

const TOKEN = "t".repeat(43);

beforeEach(() => {
  window.history.replaceState(null, "", "/verify-email");
});

afterEach(() => vi.clearAllMocks());

/// 地址栏打开带片段的落地页后渲染
function renderLanding(
  hash: string,
  submit: (token: string) => Promise<LandingOutcome<string, string>>,
) {
  window.history.replaceState(null, "", `/verify-email${hash}`);
  render(
    <TokenLandingPage<string, string>
      title="验证你的邮箱"
      description="点下面的按钮完成验证。"
      actionLabel="验证邮箱"
      pendingLabel="验证中…"
      submit={submit}
      renderSucceeded={(email) => ({
        tone: "success",
        title: "邮箱已验证",
        description: `${email} 已验证。`,
      })}
      renderInvalid={() => ({
        tone: "danger",
        title: "链接无效或已过期",
        description: "验证链接 24 小时内有效。",
      })}
      renderConflict={(email) => ({
        tone: "danger",
        title: "这个邮箱已被注册",
        description: email,
      })}
    />,
  );
}

describe("TokenLandingPage", () => {
  it("读完令牌立即从地址里抹掉片段；就绪态焦点在主按钮；打开时不提交", async () => {
    const submit = vi.fn();
    renderLanding(`#token=${TOKEN}`, submit);
    const button = await screen.findByRole("button", { name: "验证邮箱" });
    await waitFor(() => expect(button).toHaveFocus());
    expect(window.location.hash).toBe("");
    expect(window.location.pathname).toBe("/verify-email");
    expect(submit).not.toHaveBeenCalled();
  });

  it("片段里没有令牌：直接无效屏，不提交", async () => {
    const submit = vi.fn();
    renderLanding("", submit);
    expect(
      await screen.findByRole("heading", { level: 1, name: "链接无效或已过期" }),
    ).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });

  it("点按钮：进行中文案；成功屏", async () => {
    let answer: (value: LandingOutcome<string, string>) => void = () => {};
    const submit = vi.fn(
      () =>
        new Promise<LandingOutcome<string, string>>((resolve) => {
          answer = resolve;
        }),
    );
    renderLanding(`#token=${TOKEN}`, submit);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(submit).toHaveBeenCalledWith(TOKEN);
    expect(screen.getByRole("button", { name: "验证中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await act(async () => answer({ kind: "succeeded", data: "a@b.com" }));
    expect(screen.getByRole("heading", { level: 1, name: "邮箱已验证" })).toBeInTheDocument();
    expect(screen.getByText("a@b.com 已验证。")).toBeInTheDocument();
  });

  it.each([
    [{ kind: "invalid" } as const, "链接无效或已过期"],
    [{ kind: "conflict", data: "x@b.com" } as const, "这个邮箱已被注册"],
  ])("%j → 对应结果屏", async (outcome, title) => {
    renderLanding(
      `#token=${TOKEN}`,
      vi.fn(async () => outcome),
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(await screen.findByRole("heading", { level: 1, name: title })).toBeInTheDocument();
  });

  it("服务错误：回到就绪态，上方提示条", async () => {
    renderLanding(
      `#token=${TOKEN}`,
      vi.fn(async () => ({ kind: "unavailable" }) as const),
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有成功，请稍后再试");
    expect(screen.getByRole("button", { name: "验证邮箱" })).not.toHaveAttribute("aria-disabled");
  });

  it("stay：回到就绪态，不显示通用提示条", async () => {
    renderLanding(
      `#token=${TOKEN}`,
      vi.fn(async () => ({ kind: "stay" }) as const),
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "验证邮箱" })).not.toHaveAttribute("aria-disabled"),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("leaving：页面要跳走了，按钮保持进行中，不能再点", async () => {
    const submit = vi.fn(async () => ({ kind: "leaving" }) as const);
    renderLanding(`#token=${TOKEN}`, submit);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(await screen.findByRole("button", { name: "验证中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await user.click(screen.getByRole("button", { name: "验证中…" }));
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it("严格模式下 effect 跑两次：令牌只读一次，不会因为片段已被抹掉而显示无效", async () => {
    window.history.replaceState(null, "", `/verify-email#token=${TOKEN}`);
    render(
      <StrictMode>
        <TokenLandingPage<string>
          title="验证你的邮箱"
          description="点下面的按钮完成验证。"
          actionLabel="验证邮箱"
          pendingLabel="验证中…"
          submit={vi.fn()}
          renderSucceeded={() => ({ tone: "success", title: "邮箱已验证", description: "" })}
          renderInvalid={() => ({ tone: "danger", title: "链接无效或已过期", description: "" })}
        />
      </StrictMode>,
    );
    expect(await screen.findByRole("button", { name: "验证邮箱" })).not.toHaveAttribute(
      "aria-disabled",
    );
    expect(screen.queryByRole("heading", { name: "链接无效或已过期" })).not.toBeInTheDocument();
  });

  it("「返回」去首页", async () => {
    renderLanding(`#token=${TOKEN}`, vi.fn());
    expect(await screen.findByRole("link", { name: "返回" })).toHaveAttribute("href", "/");
  });
});
