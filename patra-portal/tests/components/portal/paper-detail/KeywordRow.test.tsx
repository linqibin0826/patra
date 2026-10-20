import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { KeywordRow } from "@/components/portal/paper-detail/KeywordRow";
import { deriveKeywordItems } from "@/lib/portal-api/publication-derive";

const mesh = Array.from({ length: 12 }, (_, i) => ({
  descriptorUi: `D${i}`,
  term: `Mesh ${i}`,
  major: i === 2,
}));
const keywords = Array.from({ length: 6 }, (_, i) => `kw${i}`);

describe("KeywordRow", () => {
  it("默认 8 项，主要主题词排第一；「另 10 项」点开后全部显示、按钮消失", async () => {
    const user = userEvent.setup();
    render(
      <KeywordRow items={deriveKeywordItems(makePaperDetail({ meshHeadings: mesh, keywords }))} />,
    );
    const items = () => screen.getAllByRole("listitem");
    expect(items()).toHaveLength(8);
    expect(items()[0]).toHaveTextContent("Mesh 2");
    await user.click(screen.getByRole("button", { name: "另 10 项" }));
    expect(items()).toHaveLength(18);
    expect(screen.queryByRole("button", { name: /另 \d+ 项/ })).toBeNull();
  });

  it("不超过 8 项时没有展开按钮", () => {
    render(
      <KeywordRow
        items={deriveKeywordItems(
          makePaperDetail({ meshHeadings: mesh.slice(0, 3), keywords: [] }),
        )}
      />,
    );
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("两类都为空时不渲染", () => {
    const { container } = render(
      <KeywordRow
        items={deriveKeywordItems(makePaperDetail({ meshHeadings: [], keywords: [] }))}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});

describe("deriveKeywordItems", () => {
  it("主要主题词在前，再是其余主题词与关键词；重复的关键词只留一个", () => {
    const items = deriveKeywordItems(
      makePaperDetail({ meshHeadings: mesh.slice(0, 3), keywords: ["kw", "kw", "other"] }),
    );
    expect(items.map((i) => i.text)).toEqual(["Mesh 2", "Mesh 0", "Mesh 1", "kw", "other"]);
    expect(items[0]?.major).toBe(true);
  });
});
