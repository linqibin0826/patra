import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
}));
vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn() }));
vi.mock("@/lib/identity/account", () => ({ fetchPendingEmailChange: vi.fn() }));

import AccountSettingsPage, { metadata } from "@/app/settings/account/page";
import { requireUser } from "@/lib/auth/require-user";
import { fetchPendingEmailChange } from "@/lib/identity/account";

afterEach(() => vi.clearAllMocks());

describe("/settings/account", () => {
  it("守卫用这一页的路径；标题是账号 · 设置 · Patra", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    vi.mocked(fetchPendingEmailChange).mockResolvedValue(null);
    await AccountSettingsPage();
    expect(requireUser).toHaveBeenCalledWith("/settings/account");
    expect(metadata).toMatchObject({ title: "账号 · 设置 · Patra" });
  });

  it("读待确认时会话已失效：去登录页", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    vi.mocked(fetchPendingEmailChange).mockResolvedValue("signed-out");
    await expect(AccountSettingsPage()).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fsettings%2Faccount",
    );
  });
});
