import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn() }));

import SecuritySettingsPage, { metadata } from "@/app/settings/security/page";
import { requireUser } from "@/lib/auth/require-user";

afterEach(() => vi.clearAllMocks());

describe("/settings/security", () => {
  it("守卫用这一页的路径；标题是安全 · 设置 · Patra", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    await SecuritySettingsPage();
    expect(requireUser).toHaveBeenCalledWith("/settings/security");
    expect(metadata).toMatchObject({ title: "安全 · 设置 · Patra" });
  });
});
