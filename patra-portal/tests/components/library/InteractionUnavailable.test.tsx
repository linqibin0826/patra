import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";

describe("InteractionUnavailable", () => {
  it("标题、说明与重试（客户端区块调 refetch）", async () => {
    const onRetry = vi.fn();
    render(
      <InteractionUnavailable
        title="文献库暂时打不开"
        body="请稍后再试，你收藏的文献都还在。"
        onRetry={onRetry}
      />,
    );
    expect(screen.getByRole("heading", { name: "文献库暂时打不开" })).toBeInTheDocument();
    expect(screen.getByText("请稍后再试，你收藏的文献都还在。")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalled();
  });

  it("RSC 区块：重试是指向当前地址的链接", () => {
    render(<InteractionUnavailable title="收藏夹暂时打不开" retryHref="/collections/9" />);
    expect(screen.getByRole("link", { name: "重试" })).toHaveAttribute("href", "/collections/9");
  });

  it("紧凑版：区块里用，上下留白更少、标题小一号", () => {
    render(<InteractionUnavailable compact title="讨论暂不可用" retryHref="/papers/1" />);
    const heading = screen.getByRole("heading", { name: "讨论暂不可用" });
    expect(heading).toHaveClass("text-lg");
    expect(heading.parentElement).toHaveClass("py-10");
  });
});
