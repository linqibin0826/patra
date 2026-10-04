import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { QuickSearch } from "@/components/portal/QuickSearch";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

describe("QuickSearch", () => {
  it("对话框里的检索框浮在遮罩上，用对话框阴影 shadow-xl", async () => {
    render(<QuickSearch open onOpenChange={() => {}} />);
    const dialog = await screen.findByRole("dialog", { name: "快速检索" });
    expect(dialog.querySelector("form")).toHaveClass("shadow-xl");
  });
});
