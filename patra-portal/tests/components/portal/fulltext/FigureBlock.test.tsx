import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FigureBlock } from "@/components/portal/fulltext/FigureBlock";

function renderFigure(overrides: Partial<Parameters<typeof FigureBlock>[0]> = {}) {
  return render(
    <FigureBlock
      anchorId="ft-g001"
      label="Figure 1"
      altText="Trial flow diagram"
      title={<>Trial flow diagram</>}
      caption={<p>Participants were randomised 1:1.</p>}
      src="/api/papers/42/figures/g001.webp"
      width={1600}
      height={2159}
      {...overrides}
    />,
  );
}

describe("FigureBlock", () => {
  it("图在 figure 里，alt 用图题；图注有原文小标、图题与图注正文", () => {
    renderFigure();
    const figure = screen.getByRole("figure");
    expect(figure).toHaveAttribute("id", "ft-g001");
    const img = within(figure).getByRole("img", { name: "Trial flow diagram" });
    expect(img).toHaveAttribute("src", "/api/papers/42/figures/g001.webp");
    expect(img).toHaveAttribute("width", "1600");
    expect(img).toHaveAttribute("height", "2159");
    expect(within(figure).getByText("Figure 1")).toBeInTheDocument();
    expect(within(figure).getByText("Participants were randomised 1:1.")).toBeInTheDocument();
  });

  it("点图片打开放大层：大图、图注、在新标签打开原图；关闭后焦点回到原图", async () => {
    const user = userEvent.setup();
    renderFigure();
    const trigger = screen.getByRole("button", { name: "放大查看 Figure 1" });

    await user.click(trigger);

    const dialog = await screen.findByRole("dialog", { name: "Figure 1" });
    expect(within(dialog).getByRole("img", { name: "Trial flow diagram" })).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: /在新标签打开原图/ })).toHaveAttribute(
      "href",
      "/api/papers/42/figures/g001.webp",
    );

    await user.click(within(dialog).getByRole("button", { name: "关闭" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(trigger).toHaveFocus();
  });

  it("Esc 关闭放大层", async () => {
    const user = userEvent.setup();
    renderFigure();
    await user.click(screen.getByRole("button", { name: "放大查看 Figure 1" }));
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("没有图片文件时只显示图注，不能放大", () => {
    renderFigure({ src: null });
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText("Figure 1")).toBeInTheDocument();
  });
});
