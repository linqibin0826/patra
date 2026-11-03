import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn(async () => ({})) }));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => null }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => null }));
vi.mock("@/components/history/ReadingHistoryView", () => ({ ReadingHistoryView: () => null }));

import HistoryPage, { metadata } from "@/app/history/page";
import { requireUser } from "@/lib/auth/require-user";

beforeEach(() => vi.mocked(requireUser).mockClear());

describe("/history", () => {
  it("要登录：未登录去登录页再回来", async () => {
    await HistoryPage();
    expect(requireUser).toHaveBeenCalledWith("/history");
  });

  it("页面标题；只有自己能看，不让搜索引擎收录", () => {
    expect(metadata).toMatchObject({ title: "阅读历史 · Patra", robots: { index: false } });
  });
});
