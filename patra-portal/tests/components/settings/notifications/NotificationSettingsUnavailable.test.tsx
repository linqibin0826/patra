import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationSettingsUnavailable } from "@/components/settings/notifications/NotificationSettingsUnavailable";

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

describe("NotificationSettingsUnavailable", () => {
  it("重试：重新取这一页的数据", async () => {
    render(<NotificationSettingsUnavailable />);
    await userEvent.setup().click(screen.getByRole("button", { name: "重试" }));
    expect(router.refresh).toHaveBeenCalledTimes(1);
  });
});
