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
    citeCount: 1,
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
    expect(screen.queryByRole("button", { name: /展开其余/ })).toBeNull();
  });

  it("超过 8 条默认只显示前 5 条；展开全部后不再收起", async () => {
    const user = userEvent.setup();
    renderList(items(40));
    expect(visibleItems()).toHaveLength(5);

    await user.click(screen.getByRole("button", { name: "展开其余 35 条" }));
    expect(visibleItems()).toHaveLength(40);
    expect(screen.queryByRole("button", { name: /展开其余|收起/ })).toBeNull();
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
    expect(within(second as HTMLElement).queryByRole("link", { name: /DOI|PubMed/ })).toBeNull();
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
    renderList(
      items(8).map((it) => (it.refId === "r7" ? it : { ...it, citeCount: 0 })),
      { r7: "cite-1" },
    );
    const backFrom = (n: number) =>
      within(document.getElementById(`ref-r${n}`) as HTMLElement).queryByRole("link", {
        name: /↩ 正文/,
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

  it("地址栏 hash 是畸形的百分号编码时不报错，引用跳转照常可用", async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, "", "/#%E0");
    renderList(items(40), { r7: "cite-1" });

    await user.click(screen.getByRole("link", { name: "7" }));
    expect(document.getElementById("ref-r7")).toBeVisible();
  });
});

describe("ReferenceList · 标签栏排版（PAP-89）", () => {
  it("序号补成两位放在标签格；原文标签不是数字时照原文", () => {
    renderList([...items(1), ...items(1, { anchorId: "ref-ra", refId: "ra", label: "a" })]);
    const [first, second] = visibleItems();
    expect((first as HTMLElement).firstElementChild).toHaveTextContent(/^01$/);
    expect((second as HTMLElement).firstElementChild).toHaveTextContent(/^a$/);
  });

  it("「↩ 正文 N 处」：被引 2 次写次数，1 次只写「↩ 正文」，0 次不显示", () => {
    renderList(
      [
        ...items(1, { citeCount: 2 }),
        ...items(1, { anchorId: "ref-r2", refId: "r2", label: "2", citeCount: 1 }),
        ...items(1, { anchorId: "ref-r3", refId: "r3", label: "3", citeCount: 0 }),
      ],
      { r1: "cite-1", r2: "cite-1", r3: "cite-1" },
    );
    const [a, b, c] = visibleItems() as HTMLElement[];
    expect(within(a as HTMLElement).getByRole("link", { name: "↩ 正文 2 处" })).toBeInTheDocument();
    expect(within(b as HTMLElement).getByRole("link", { name: "↩ 正文" })).toBeInTheDocument();
    expect(within(c as HTMLElement).queryByRole("link", { name: /↩/ })).toBeNull();
  });

  it("PubMed 链接写出 PMID", () => {
    renderList(items(1, { pmid: "34815628" }));
    expect(screen.getByRole("link", { name: "PubMed 34815628 ↗" })).toBeInTheDocument();
  });

  it("被 preventDefault 的点击不展开、不高亮（引用卡片接管了这次点击）", () => {
    renderList(items(40), { r7: "cite-1" });
    const link = screen.getByRole("link", { name: "7" });
    const event = new MouseEvent("click", { bubbles: true, cancelable: true });
    event.preventDefault();
    link.dispatchEvent(event);
    expect(document.getElementById("ref-r7")).not.toBeVisible();
  });

  it("带 data-cite-from 的链接被点后，「↩」回到它指明的引用", async () => {
    const user = userEvent.setup();
    render(
      <>
        <a id="cite-7" href="#ref-r2" data-cite-ref="r2">
          seven
        </a>
        <a href="#ref-r2" data-cite-ref="r2" data-cite-from="cite-7">
          跳到文末
        </a>
        <ReferenceList items={items(3)} firstCiteByRef={{ r2: "cite-1" }} />
      </>,
    );
    await user.click(screen.getByRole("link", { name: "跳到文末" }));
    expect(
      within(document.getElementById("ref-r2") as HTMLElement).getByRole("link", { name: /↩/ }),
    ).toHaveAttribute("href", "#cite-7");
  });
});
