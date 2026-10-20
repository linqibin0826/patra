import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DetailCrumbs } from "@/components/portal/DetailCrumbs";

const trail = [
  { label: "Patra", href: "/" },
  { label: "文献", href: "/papers" },
];

describe("DetailCrumbs", () => {
  it("status 插槽渲染在当前页与右端操作之间", () => {
    render(
      <DetailCrumbs
        trail={trail}
        current="PMID 1"
        status={<span>STATUS</span>}
        action={<button type="button">目录</button>}
      />,
    );
    const items = within(screen.getByRole("navigation", { name: "面包屑" })).getAllByRole(
      "listitem",
    );
    const texts = items.map((li) => li.textContent);
    expect(texts.indexOf("PMID 1")).toBeLessThan(texts.indexOf("STATUS"));
    expect(texts.indexOf("STATUS")).toBeLessThan(texts.indexOf("目录"));
  });

  it("不传 status（期刊详情页）时不多出列表项", () => {
    render(<DetailCrumbs trail={trail} current="NEJM" />);
    const items = within(screen.getByRole("navigation", { name: "面包屑" })).getAllByRole(
      "listitem",
    );
    expect(items.map((li) => li.textContent)).toEqual(["Patra", "文献", "NEJM"]);
  });
});
