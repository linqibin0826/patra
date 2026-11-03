import { fireEvent, screen, within } from "@testing-library/react";
import { makePaper } from "@tests/fixtures/paper";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/analytics/insight", () => ({ fetchAlsoRead: vi.fn() }));
vi.mock("@/lib/portal-api/publications", () => ({ fetchPublicationCards: vi.fn() }));
vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));

import { AlsoReadSection } from "@/components/portal/paper-detail/AlsoReadSection";
import { AlsoReadSkeleton } from "@/components/portal/paper-detail/AlsoReadSkeleton";
import { track } from "@/lib/analytics/client/queue";
import { fetchAlsoRead } from "@/lib/analytics/insight";
import { fetchPublicationCards } from "@/lib/portal-api/publications";

const paper = (id: string, title: string) =>
  makePaper({ id, title, journal: "JAMA", authors: ["Marik PE", "Khangoora V"], kind: "RCT" });

async function render(paperId = "9") {
  const ui = await AlsoReadSection({ paperId });
  return renderWithQuery(<div>{ui}</div>);
}

beforeEach(() => {
  vi.mocked(fetchAlsoRead).mockReset();
  vi.mocked(fetchPublicationCards).mockReset();
  vi.mocked(track).mockReset();
});

describe("看过这篇的人还看了 / 同主题文献（简报 v0.18 §3.6）", () => {
  it("paper.also.full：一组「看过这篇的人还看了」，卡片写来源年份类型、标题、刊名 · 第一作者「等」", async () => {
    vi.mocked(fetchAlsoRead).mockResolvedValue([
      { articleId: "1", source: "CO_READ", meshTerm: null, venueName: null },
    ]);
    vi.mocked(fetchPublicationCards).mockResolvedValue([paper("1", "VITAMINS")]);
    await render();
    const section = screen.getByRole("region", { name: "看过这篇的人还看了" });
    expect(within(section).getByText("常和这篇在同一天被同一批读者读过的文献")).toBeInTheDocument();
    expect(within(section).getByRole("link", { name: "VITAMINS" })).toHaveAttribute(
      "href",
      "/papers/1",
    );
    expect(within(section).getByText("JAMA")).toBeInTheDocument();
    expect(within(section).getByText(/Marik PE 等/)).toBeInTheDocument();
    expect(within(section).queryByRole("button")).toBeNull();
  });

  it("paper.also.mixed：两组，补位卡片写原因", async () => {
    vi.mocked(fetchAlsoRead).mockResolvedValue([
      { articleId: "1", source: "CO_READ", meshTerm: null, venueName: null },
      { articleId: "2", source: "SAME_MESH", meshTerm: "Shock, Septic", venueName: null },
      { articleId: "3", source: "SAME_VENUE", meshTerm: null, venueName: "Chest" },
    ]);
    vi.mocked(fetchPublicationCards).mockResolvedValue([
      paper("1", "One"),
      paper("2", "Two"),
      paper("3", "Three"),
    ]);
    await render();
    expect(screen.getByRole("region", { name: "看过这篇的人还看了" })).toBeInTheDocument();
    const topic = screen.getByRole("region", { name: "同主题文献" });
    expect(
      within(topic).getByText("主要主题词与这篇相同；不够时再看同一本期刊"),
    ).toBeInTheDocument();
    expect(within(topic).getByText("Shock, Septic")).toBeInTheDocument();
    expect(within(topic).getByText(/同主题词/)).toBeInTheDocument();
    expect(within(topic).getByText(/同一期刊/)).toHaveTextContent("同一期刊 Chest");
  });

  it("补位没给主题词：不出空着的原因行；同期刊没给刊名时用卡片的刊名", async () => {
    vi.mocked(fetchAlsoRead).mockResolvedValue([
      { articleId: "2", source: "SAME_MESH", meshTerm: null, venueName: null },
      { articleId: "3", source: "SAME_VENUE", meshTerm: null, venueName: null },
    ]);
    vi.mocked(fetchPublicationCards).mockResolvedValue([paper("2", "Two"), paper("3", "Three")]);
    await render();
    expect(screen.queryByText(/同主题词/)).toBeNull();
    expect(screen.getByText(/同一期刊/)).toHaveTextContent("同一期刊 JAMA");
  });

  it("paper.also.same：只有同主题一组", async () => {
    vi.mocked(fetchAlsoRead).mockResolvedValue([
      { articleId: "2", source: "SAME_MESH", meshTerm: "Sepsis", venueName: null },
    ]);
    vi.mocked(fetchPublicationCards).mockResolvedValue([paper("2", "Two")]);
    await render();
    expect(screen.queryByRole("region", { name: "看过这篇的人还看了" })).toBeNull();
    expect(screen.getByRole("region", { name: "同主题文献" })).toBeInTheDocument();
  });

  it.each([
    ["paper.also.none：没有推荐", () => vi.mocked(fetchAlsoRead).mockResolvedValue([])],
    ["行为数据服务取不到", () => vi.mocked(fetchAlsoRead).mockResolvedValue(null)],
    [
      "catalog 取不到",
      () => {
        vi.mocked(fetchAlsoRead).mockResolvedValue([
          { articleId: "1", source: "CO_READ", meshTerm: null, venueName: null },
        ]);
        vi.mocked(fetchPublicationCards).mockRejectedValue(new Error("boom"));
      },
    ],
    [
      "catalog 一篇都没回",
      () => {
        vi.mocked(fetchAlsoRead).mockResolvedValue([
          { articleId: "1", source: "CO_READ", meshTerm: null, venueName: null },
        ]);
        vi.mocked(fetchPublicationCards).mockResolvedValue([]);
      },
    ],
  ])("%s：整块不出现", async (_, arrange) => {
    arrange();
    const ui = await AlsoReadSection({ paperId: "9" });
    expect(ui).toBeNull();
  });

  it("两组各是一个列表实例：related / related_topic，位置各从 1 起", async () => {
    vi.mocked(fetchAlsoRead).mockResolvedValue([
      { articleId: "1", source: "CO_READ", meshTerm: null, venueName: null },
      { articleId: "2", source: "SAME_MESH", meshTerm: "Sepsis", venueName: null },
    ]);
    vi.mocked(fetchPublicationCards).mockResolvedValue([paper("1", "One"), paper("2", "Two")]);
    await render();
    fireEvent.click(screen.getByRole("link", { name: "Two" }));
    expect(track).toHaveBeenCalledWith(
      "article_clicked",
      expect.objectContaining({ article_id: "2", list_type: "related_topic", position: 1 }),
    );
  });

  it("paper.also.loading：标题条 + 3 张卡片骨架", () => {
    renderWithQuery(<AlsoReadSkeleton />);
    expect(screen.getByLabelText("正在加载推荐")).toBeInTheDocument();
  });
});
