import { screen, within } from "@testing-library/react";
import { makeDetail } from "@tests/fixtures/experience";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/navigation")>()),
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  usePathname: () => "/posts/9",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/components/portal/TopNav", () => ({ TopNav: () => <nav aria-label="导航栏桩" /> }));
vi.mock("@/components/portal/Footer", () => ({ Footer: () => <footer /> }));
vi.mock("@/components/experience/PostView", () => ({
  PostView: () => <article aria-label="经验正文桩" />,
}));
vi.mock("@/components/discussion/DiscussionSection", () => ({
  DiscussionSection: (props: { subject: { type: string; key: string }; basePath: string }) => (
    <section id="discussion" data-subject={`${props.subject.type}/${props.subject.key}`} />
  ),
}));
vi.mock("@/lib/interaction/experience", () => ({ fetchPost: vi.fn() }));
vi.mock("@/lib/portal-api/venues", () => ({ fetchVenueDetail: vi.fn() }));
vi.mock("@/lib/public-url", () => ({
  publicUrl: vi.fn(async (path: string) => `http://mini${path}`),
}));

import PostPage, { generateMetadata } from "@/app/posts/[id]/page";
import { fetchPost } from "@/lib/interaction/experience";
import { fetchVenueDetail } from "@/lib/portal-api/venues";

const params = (id = "9") => ({
  params: Promise.resolve({ id }),
  searchParams: Promise.resolve({}),
});

beforeEach(() => {
  vi.mocked(fetchVenueDetail).mockResolvedValue(
    makeVenueDetail({
      id: "100",
      title: "Journal of the American Heart Association",
      abbreviatedTitle: "J Am Heart Assoc",
      issnL: "2047-9980",
      isOpenAccess: true,
    }),
  );
});

async function renderPage(id = "9") {
  return renderWithQuery(await PostPage(params(id)));
}

describe("经验页的读状态（spec 8.1）", () => {
  it("不存在：notFound()", async () => {
    vi.mocked(fetchPost).mockResolvedValue({ kind: "not-found" });
    await expect(PostPage(params())).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("正常：主栏、侧栏期刊卡、回复区挂在这条经验下", async () => {
    vi.mocked(fetchPost).mockResolvedValue({ kind: "ok", data: makeDetail() });
    const { container } = await renderPage();
    expect(screen.getByRole("article", { name: "经验正文桩" })).toBeInTheDocument();
    expect(container.querySelector("#discussion")).toHaveAttribute("data-subject", "post/9");
    const rail = screen.getByRole("complementary");
    expect(rail).toHaveTextContent("J Am Heart Assoc · ISSN 2047-9980");
    expect(rail).toHaveTextContent("开放获取");
    expect(within(rail).getByRole("link", { name: "这本期刊的其他投稿经验 →" })).toHaveAttribute(
      "href",
      "/journals/100#experiences",
    );
    const crumbs = screen.getByRole("navigation", { name: "面包屑" });
    expect(within(crumbs).getByRole("link", { name: "J Am Heart Assoc" })).toHaveAttribute(
      "href",
      "/journals/100#experiences",
    );
    expect(crumbs).toHaveTextContent("投稿经验");
  });

  it("post.deleted：该经验已删除，没有回复区", async () => {
    vi.mocked(fetchPost).mockResolvedValue({
      kind: "ok",
      data: { ...makeDetail(), deleted: true, experience: null },
    });
    const { container } = await renderPage();
    expect(screen.getByRole("heading", { name: "该经验已删除" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "作者删除了这条投稿经验，它已经从期刊页的列表和统计中去掉，下面的回复也不再显示。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "看这本期刊的其他投稿经验 →" })).toHaveAttribute(
      "href",
      "/journals/100#experiences",
    );
    expect(container.querySelector("#discussion")).toBeNull();
  });

  it("post.unavailable：整块错误，不是 404", async () => {
    vi.mocked(fetchPost).mockResolvedValue({ kind: "unavailable" });
    await renderPage();
    expect(screen.getByText("投稿经验暂时打不开")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "重试" })).toHaveAttribute("href", "/posts/9");
  });

  it("期刊已失效：页面上没有去期刊页的链接（Review Focus 4）", async () => {
    vi.mocked(fetchPost).mockResolvedValue({
      kind: "ok",
      data: { ...makeDetail(), journal: { ...makeDetail().journal, state: "DELETED" } },
    });
    vi.mocked(fetchVenueDetail).mockResolvedValue(null);
    const { container } = await renderPage();
    const journalLinks = [...container.querySelectorAll("a")].filter((link) =>
      link.getAttribute("href")?.startsWith("/journals/"),
    );
    expect(journalLinks).toHaveLength(0);
    expect(screen.getByRole("complementary")).toHaveTextContent(
      "Journal of the American Heart Association",
    );
  });

  it("期刊卡片取不到时退回快照刊名", async () => {
    vi.mocked(fetchPost).mockResolvedValue({ kind: "ok", data: makeDetail() });
    vi.mocked(fetchVenueDetail).mockRejectedValue(new Error("catalog down"));
    await renderPage();
    const rail = screen.getByRole("complementary");
    expect(rail).toHaveTextContent("Journal of the American Heart Association");
    expect(rail).not.toHaveTextContent("ISSN");
  });
});

describe("页面信息（spec 8.5）", () => {
  it("标题、描述、canonical；不出现作者", async () => {
    vi.mocked(fetchPost).mockResolvedValue({
      kind: "ok",
      data: makeDetail({ body: "**审稿人**有两位，[详见](https://x.y)。" }),
    });
    const metadata = await generateMetadata(params());
    expect(metadata.title).toBe("J Am Heart Assoc 投稿经验 · 2026 年 3 月投稿 · Patra");
    expect(metadata.description).toBe("首轮 42 天 · 大修 → 录用。审稿人有两位，详见。");
    expect(metadata.alternates?.canonical).toBe("http://mini/posts/9");
    expect(JSON.stringify(metadata)).not.toMatch(/chen-yu|陈宇|"7"/);
  });

  it("正文只取前 100 字", async () => {
    vi.mocked(fetchPost).mockResolvedValue({
      kind: "ok",
      data: makeDetail({ body: "字".repeat(150) }),
    });
    const metadata = await generateMetadata(params());
    expect(metadata.description).toBe(`首轮 42 天 · 大修 → 录用。${"字".repeat(100)}…`);
  });

  it("已删除：noindex", async () => {
    vi.mocked(fetchPost).mockResolvedValue({
      kind: "ok",
      data: { ...makeDetail(), deleted: true, experience: null },
    });
    const metadata = await generateMetadata(params());
    expect(metadata.title).toBe("该经验已删除 · Patra");
    expect(metadata.robots).toEqual({ index: false });
  });

  it("取不到时不设", async () => {
    vi.mocked(fetchPost).mockResolvedValue({ kind: "unavailable" });
    expect(await generateMetadata(params())).toEqual({});
  });
});
