import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
}));

import SettingsIndexPage from "@/app/settings/page";

describe("/settings", () => {
  it("跳到个人资料分区", () => {
    expect(() => SettingsIndexPage()).toThrow("NEXT_REDIRECT:/settings/profile");
  });
});
