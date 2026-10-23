import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn() }));

import AccountSettingsPage, { metadata } from "@/app/settings/account/page";
import { requireUser } from "@/lib/auth/require-user";

afterEach(() => vi.clearAllMocks());

describe("/settings/account", () => {
  it("守卫用这一页的路径；标题是账号 · 设置 · Patra", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    await AccountSettingsPage();
    expect(requireUser).toHaveBeenCalledWith("/settings/account");
    expect(metadata).toMatchObject({ title: "账号 · 设置 · Patra" });
  });
});
