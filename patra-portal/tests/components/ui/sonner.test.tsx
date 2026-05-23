import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Toaster } from "@/components/ui/sonner";

// next-themes useTheme mock
vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: "light" }),
}));

describe("Toaster", () => {
  it("render 不崩", () => {
    const { container } = render(<Toaster />);
    // sonner 渲染到 portal 内，container 本身为空但不会抛错
    expect(container).toBeTruthy();
  });

  it("透传 position prop 不报错", () => {
    const { unmount } = render(<Toaster position="top-right" />);
    expect(() => unmount()).not.toThrow();
  });
});
