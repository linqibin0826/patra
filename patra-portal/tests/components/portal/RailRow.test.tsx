import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RailRow } from "@/components/portal/RailRow";

describe("RailRow", () => {
  it("有标签时左格放标签、右格放内容；main 容器窄于 580px 时改单列", () => {
    const { container } = render(<RailRow label="OBJECTIVE">Body text.</RailRow>);
    const row = container.firstElementChild as HTMLElement;
    expect(row.className).toContain("grid-cols-[128px_minmax(0,1fr)]");
    expect(row.className).toContain("@max-[580px]/main:grid-cols-1");
    expect(row.children).toHaveLength(2);
    expect(row.children[0]).toHaveTextContent("OBJECTIVE");
    expect(row.children[1]).toHaveTextContent("Body text.");
  });

  it("没有标签时标签格仍占位，读屏跳过", () => {
    const { container } = render(<RailRow>Body text.</RailRow>);
    const row = container.firstElementChild as HTMLElement;
    expect(row.children[0]).toHaveAttribute("aria-hidden", "true");
    expect(row.children[0]).toBeEmptyDOMElement();
  });

  it("wide 时内容从标签格左缘横跨到正文右缘，最宽 772px，不再有标签格", () => {
    const { container } = render(<RailRow wide>Figure</RailRow>);
    const row = container.firstElementChild as HTMLElement;
    expect(row.children).toHaveLength(1);
    expect(row.children[0]?.className).toContain("col-span-full");
    expect(row.children[0]?.className).toContain("max-w-[772px]");
  });

  it("as 可换成 li、header", () => {
    render(
      <ul>
        <RailRow as="li" label="01">
          Item
        </RailRow>
      </ul>,
    );
    expect(screen.getByRole("listitem")).toHaveTextContent("01Item");
  });
});
