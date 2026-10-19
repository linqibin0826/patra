import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ReferenceList, type ReferenceListItem } from "@/components/portal/fulltext/ReferenceList";

function items(n: number, overrides: Partial<ReferenceListItem> = {}): ReferenceListItem[] {
  return Array.from({ length: n }, (_, i) => ({
    anchorId: `ref-r${i + 1}`,
    refId: `r${i + 1}`,
    label: `${i + 1}`,
    content: `Author ${i + 1}. Title ${i + 1}.`,
    doi: null,
    pmid: null,
    ...overrides,
  }));
}

function renderList(list: ReferenceListItem[], firstCiteByRef: Record<string, string> = {}) {
  return render(
    <>
      <p>
        Body text{" "}
        <a id="cite-1" href="#ref-r7" data-cite-ref="r7">
          7
        </a>{" "}
        and again{" "}
        <a id="cite-2" href="#ref-r7" data-cite-ref="r7">
          7b
        </a>
      </p>
      <ReferenceList items={list} firstCiteByRef={firstCiteByRef} />
    </>,
  );
}

const visibleItems = () =>
  within(screen.getByRole("list", { name: "参考文献" })).getAllByRole("listitem");

afterEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("ReferenceList", () => {
  it("标题带条目数；不超过 8 条直接全显示", () => {
    renderList(items(8));
    expect(screen.getByRole("heading", { level: 2, name: /参考文献\s*8/ })).toBeInTheDocument();
    expect(visibleItems()).toHaveLength(8);
    expect(screen.queryByRole("button", { name: /展开全部/ })).toBeNull();
  });

  it("超过 8 条默认只显示前 5 条；展开全部后不再收起", async () => {
    const user = userEvent.setup();
    renderList(items(40));
    expect(visibleItems()).toHaveLength(5);

    await user.click(screen.getByRole("button", { name: "展开全部 40 条" }));
    expect(visibleItems()).toHaveLength(40);
    expect(screen.queryByRole("button", { name: /展开全部|收起/ })).toBeNull();
  });

  it("有 DOI / PMID 时给外链，没有就不显示外链行", () => {
    renderList([
      ...items(1, { doi: "10.1/x", pmid: "123" }),
      ...items(1, { anchorId: "ref-r2", refId: "r2", label: "2" }),
    ]);
    const [first, second] = visibleItems();
    expect(within(first as HTMLElement).getByRole("link", { name: /DOI/ })).toHaveAttribute(
      "href",
      "https://doi.org/10.1/x",
    );
    expect(within(first as HTMLElement).getByRole("link", { name: /PubMed/ })).toHaveAttribute(
      "href",
      "https://pubmed.ncbi.nlm.nih.gov/123/",
    );
    expect(within(second as HTMLElement).queryByRole("link")).toBeNull();
  });

  it("点正文里的引用：收起的列表自动展开，目标条目亮起，地址栏更新为该条锚点", async () => {
    const user = userEvent.setup();
    renderList(items(40), { r7: "cite-1" });

    await user.click(screen.getByRole("link", { name: "7" }));

    const target = document.getElementById("ref-r7");
    expect(target).toBeVisible();
    expect(target).toHaveAttribute("data-highlighted", "true");
    expect(window.location.hash).toBe("#ref-r7");
  });

  it("「回到正文」回到刚才点的那个引用；没点过时回到第一次被引用的位置；没被引用过不显示", async () => {
    const user = userEvent.setup();
    renderList(items(8), { r7: "cite-1" });
    const backFrom = (n: number) =>
      within(document.getElementById(`ref-r${n}`) as HTMLElement).queryByRole("link", {
        name: /回到正文/,
      });

    expect(backFrom(7)).toHaveAttribute("href", "#cite-1");
    expect(backFrom(1)).toBeNull();

    await user.click(screen.getByRole("link", { name: "7b" }));
    expect(backFrom(7)).toHaveAttribute("href", "#cite-2");
  });

  it("直接打开 #ref-N 且该条在收起部分时，自动展开", () => {
    window.history.replaceState(null, "", "/#ref-r30");
    renderList(items(40));
    expect(document.getElementById("ref-r30")).toBeVisible();
  });
});
