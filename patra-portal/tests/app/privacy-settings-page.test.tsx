import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
}));
vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn(async () => ({ username: "c" })) }));
vi.mock("@/lib/identity/profile", () => ({ fetchMyPrivacy: vi.fn() }));
vi.mock("@/lib/analytics/history", () => ({ fetchReadingRecording: vi.fn() }));
vi.mock("@/components/settings/privacy/PrivacyForm", () => ({
  PrivacyForm: ({ readingCard }: { readingCard: ReactNode }) => (
    <div>
      <p>三张隐私卡片</p>
      {readingCard}
    </div>
  ),
}));
vi.mock("@/components/settings/privacy/ReadingRecordCard", () => ({
  ReadingRecordCard: ({ initial }: { initial: unknown }) => (
    <p>阅读记录卡片：{JSON.stringify(initial)}</p>
  ),
}));

import PrivacySettingsPage from "@/app/settings/privacy/page";
import { ReadingRecordSection } from "@/components/settings/privacy/ReadingRecordSection";
import { fetchReadingRecording } from "@/lib/analytics/history";
import { fetchMyPrivacy } from "@/lib/identity/profile";

beforeEach(() => {
  vi.mocked(fetchReadingRecording).mockReset();
  vi.mocked(fetchMyPrivacy).mockResolvedValue({
    profileVisibility: "PUBLIC",
    professionalVisibility: "PUBLIC",
    searchIndexing: true,
  } as never);
});

describe("/settings/privacy · 阅读记录卡片", () => {
  it("页面不等行为数据服务：上面三张先出，第四张在自己的 Suspense 里（先出骨架）", async () => {
    vi.mocked(fetchReadingRecording).mockReturnValue(new Promise(() => {}));
    render(await PrivacySettingsPage());
    expect(screen.getByText("三张隐私卡片")).toBeInTheDocument();
    expect(screen.getByLabelText("正在加载阅读记录设置")).toBeInTheDocument();
  });

  it("取到开关：卡片拿到当前值", async () => {
    vi.mocked(fetchReadingRecording).mockResolvedValue({ kind: "ok", data: { enabled: false } });
    render(await ReadingRecordSection());
    expect(screen.getByText('阅读记录卡片：{"enabled":false}')).toBeInTheDocument();
  });

  it("取不到：卡片降级", async () => {
    vi.mocked(fetchReadingRecording).mockResolvedValue({ kind: "unavailable" });
    render(await ReadingRecordSection());
    expect(screen.getByText('阅读记录卡片："unavailable"')).toBeInTheDocument();
  });
});
