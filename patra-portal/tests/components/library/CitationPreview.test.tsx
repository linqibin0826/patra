import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CitationPreview } from "@/components/library/CitationPreview";

describe("CitationPreview", () => {
  it("shows two skeleton lines while loading", () => {
    const { container } = render(<CitationPreview lines={[]} state="loading" onRetry={vi.fn()} />);
    expect(container.querySelectorAll(".shimmer")).toHaveLength(2);
    expect(screen.queryByText("引用暂时生成不了")).not.toBeInTheDocument();
  });

  it("shows error with retry", async () => {
    const onRetry = vi.fn();
    render(<CitationPreview lines={[]} state="error" onRetry={onRetry} />);
    expect(screen.getByText("引用暂时生成不了")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("renders one paragraph per line", () => {
    render(<CitationPreview lines={["[1] One.", "[2] Two."]} state="ready" onRetry={vi.fn()} />);
    expect(screen.getByText("[1] One.").tagName).toBe("P");
    expect(screen.getByText("[2] Two.")).toBeInTheDocument();
  });
});
