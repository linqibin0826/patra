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
      layout="portrait"
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

describe("FigureBlock · 宽度分类（PAP-89）", () => {
  const frame = () => screen.getByRole("button", { name: /放大查看 Figure 1/ });

  it("text：图框最宽 612px，与正文同宽", () => {
    renderFigure({ layout: "text", width: 733, height: 558 });
    expect(frame().closest("[data-layout]")).toHaveAttribute("data-layout", "text");
    expect(frame().closest("[data-layout]")?.className).toContain("max-w-[612px]");
  });

  it("wide：图框横跨标签栏到正文右缘，最宽 772px", () => {
    renderFigure({ layout: "wide", width: 750, height: 420 });
    expect(frame().closest("[data-layout]")?.className).toContain("max-w-[772px]");
  });

  it("portrait：内容格内居中，图片最高 640px", () => {
    renderFigure({ layout: "portrait" });
    expect(screen.getByRole("img", { name: "Trial flow diagram" }).className).toContain(
      "max-h-[640px]",
    );
  });

  it("图片带原图宽高属性且 h-auto，加载前按比例占位、不跳；不超过原图宽", () => {
    renderFigure({ layout: "wide-zoom", width: 750, height: 280 });
    const img = screen.getByRole("img", { name: "Trial flow diagram" });
    expect(img).toHaveAttribute("width", "750");
    expect(img).toHaveAttribute("height", "280");
    expect(img.className).toContain("h-auto");
    expect(img.className).toContain("max-w-full");
  });

  it("标签栏放原文标签（去掉末尾句点由调用方处理）；图题不加粗", () => {
    renderFigure({ layout: "text", label: "Figure 2" });
    const label = screen.getByText("Figure 2");
    expect(label.closest(".grid")?.firstElementChild).toContainElement(label);
    expect(screen.getAllByText("Trial flow diagram").at(-1)?.className).not.toContain(
      "font-medium",
    );
  });

  it("wide-zoom 常显「放大查看」，点它打开放大层；text 时它只在悬停或聚焦图框时出现", async () => {
    const user = userEvent.setup();
    const { unmount } = renderFigure({ layout: "wide-zoom", width: 750, height: 280 });
    const zoom = screen.getByRole("button", { name: "放大查看" });
    expect(zoom.parentElement?.className).not.toContain("opacity-0");
    await user.click(zoom);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    unmount();

    renderFigure({ layout: "text", width: 733, height: 558 });
    expect(screen.getByRole("button", { name: "放大查看" }).parentElement?.className).toContain(
      "opacity-0",
    );
  });

  it("「原图 ↗」在新标签打开图片地址", () => {
    renderFigure({ layout: "text" });
    expect(screen.getByRole("link", { name: "原图 ↗" })).toHaveAttribute(
      "href",
      "/api/papers/42/figures/g001.webp",
    );
    expect(screen.getByRole("link", { name: "原图 ↗" })).toHaveAttribute("target", "_blank");
  });
});
