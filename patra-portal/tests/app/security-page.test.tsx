import { render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn() }));
vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));
vi.mock("@/components/settings/security/ChangePasswordCard", () => ({
  ChangePasswordCard: () => <div>修改密码卡片</div>,
}));
vi.mock("@/components/settings/security/SessionsCard", () => ({
  SessionsCard: () => <div>登录设备卡片</div>,
}));
vi.mock("@/components/settings/security/LoginMethodsCard", () => ({
  LoginMethodsCard: (props: unknown) => <div>{JSON.stringify(props)}</div>,
}));

import SecuritySettingsPage, { metadata } from "@/app/settings/security/page";
import { requireUser } from "@/lib/auth/require-user";

beforeEach(() => {
  resetCookieJar({}, { host: "patra.linqibin.dev" });
  vi.mocked(requireUser).mockResolvedValue(USER);
});
afterEach(() => vi.clearAllMocks());

async function renderPage(params: Record<string, string> = {}) {
  render(await SecuritySettingsPage({ searchParams: Promise.resolve(params) }));
}

describe("/settings/security", () => {
  it("守卫用这一页的路径；标题是安全 · 设置 · Patra", async () => {
    await renderPage();
    expect(requireUser).toHaveBeenCalledWith("/settings/security");
    expect(metadata).toMatchObject({ title: "安全 · 设置 · Patra" });
  });

  it("有密码：登录方式、修改密码、登录设备依次都有", async () => {
    await renderPage();
    const text = document.body.textContent ?? "";
    expect(text.indexOf("emailVerified")).toBeLessThan(text.indexOf("修改密码卡片"));
    expect(text.indexOf("修改密码卡片")).toBeLessThan(text.indexOf("登录设备卡片"));
  });

  it("没有密码：不显示修改密码，填不了当前密码", async () => {
    vi.mocked(requireUser).mockResolvedValue({ ...USER, hasPassword: false });
    await renderPage();
    expect(screen.queryByText("修改密码卡片")).not.toBeInTheDocument();
    expect(screen.getByText("登录设备卡片")).toBeInTheDocument();
  });

  it("把公网判断、邮箱状态和绑定结果交给卡片", async () => {
    await renderPage({ oauth: "taken", provider: "orcid" });
    const props = JSON.parse(screen.getByText(/oauthEnabled/).textContent ?? "{}");
    expect(props).toEqual({
      oauthEnabled: true,
      email: USER.email,
      emailVerified: USER.emailVerified,
      notice: { kind: "taken", provider: "orcid" },
    });
  });

  it("tailnet 地址：oauthEnabled 为 false", async () => {
    resetCookieJar({}, { host: "100.103.73.27:4000" });
    await renderPage();
    expect(JSON.parse(screen.getByText(/oauthEnabled/).textContent ?? "{}").oauthEnabled).toBe(
      false,
    );
  });
});
