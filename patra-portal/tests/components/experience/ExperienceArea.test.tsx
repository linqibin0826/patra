import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  makeDetail,
  makeExperience,
  makeFewSummary,
  makeSummary,
} from "@tests/fixtures/experience";
import { USER } from "@tests/fixtures/user";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { QueryClient } from "@tanstack/react-query";
import { ExperienceArea } from "@/components/experience/ExperienceArea";
import type { CurrentUser } from "@/lib/auth/types";
import type { ViewerSeed } from "@/lib/discussion/viewer";
import { publishExperience } from "@/lib/interaction/experience-actions";
import type { ExperiencePage, Summary } from "@/lib/interaction/experience-schema";
import { queryKeys } from "@/lib/query-keys";

const toastSuccess = vi.fn();
vi.mock("sonner", () => ({ toast: { success: (...args: unknown[]) => toastSuccess(...args) } }));
vi.mock("@/lib/interaction/experience-actions", () => ({
  publishExperience: vi.fn(),
  editExperience: vi.fn(),
  deleteExperience: vi.fn(),
}));
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/journals/100",
  useSearchParams: () => params,
}));

let params = new URLSearchParams();

const fetchMock = vi.fn<typeof fetch>();
const publish = vi.mocked(publishExperience);
const NOW = Date.parse("2026-10-28T02:00:00Z");
const VERIFIED: ViewerSeed = {
  status: "authenticated",
  emailVerified: true,
  email: USER.email,
  avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
};

beforeEach(() => {
  params = new URLSearchParams();
  window.history.replaceState(null, "", "/journals/100");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  vi.clearAllMocks();
});

const page = (items = [makeExperience()], nextCursor: string | null = null): ExperiencePage => ({
  items,
  nextCursor,
  total: items.length,
});

/// 首屏数据：汇总与列表第一页都在缓存里
function seed(summary: Summary = makeSummary(), first: ExperiencePage = page()) {
  return (client: QueryClient) => {
    client.setQueryData(queryKeys.experienceSummary("100", "3y"), summary);
    client.setQueryData(queryKeys.experienceList("100", "latest"), {
      pages: [first],
      pageParams: [null],
    });
  };
}

function renderArea(
  options: {
    user?: CurrentUser | null;
    viewer?: ViewerSeed;
    seed?: (client: QueryClient) => void;
  } = {},
) {
  return renderDiscussion(
    <ExperienceArea
      journalId="100"
      journalTitle="Journal of the American Heart Association"
      viewer={options.viewer ?? VERIFIED}
      now={NOW}
    />,
    { user: options.user, seed: options.seed ?? seed() },
  );
}

describe("写投稿经验按钮的三种身份（简报 §3.14）", () => {
  it("匿名访客：登录后写投稿经验", () => {
    renderArea({ user: null, viewer: { status: "anonymous" } });
    expect(screen.getByRole("link", { name: "登录后写投稿经验" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F100",
    );
  });

  it("邮箱未验证：按钮禁用并提示", () => {
    renderArea({ user: { ...USER, emailVerified: false } });
    expect(screen.getByRole("button", { name: "写投稿经验" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByText("验证邮箱后才能写投稿经验")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });

  it("当前用户变成匿名后按钮跟着变（Review Focus 2）", async () => {
    const { client } = renderArea();
    expect(screen.getByRole("button", { name: "写投稿经验" })).toBeEnabled();
    act(() => client.setQueryData(queryKeys.currentUser, null));
    expect(await screen.findByRole("link", { name: "登录后写投稿经验" })).toBeInTheDocument();
  });
});

describe("表单与发布", () => {
  it("点开后按钮隐藏、表单在汇总之前", async () => {
    const user = userEvent.setup();
    renderArea();
    await user.click(screen.getByRole("button", { name: "写投稿经验" }));
    expect(screen.queryByRole("button", { name: "写投稿经验" })).not.toBeInTheDocument();
    const form = screen.getByRole("form", { name: "写投稿经验" });
    const card = screen.getByRole("article", { name: "同行经验" });
    expect(form.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("发布成功：表单收起、toast 带查看、重取汇总与列表", async () => {
    publish.mockResolvedValue({ ok: true, detail: makeDetail({ postId: "55" }) });
    fetchMock.mockImplementation(async (input) =>
      String(input).includes("summary")
        ? Response.json(makeSummary({ count: 16 }))
        : Response.json(page()),
    );
    const user = userEvent.setup();
    renderArea();
    await user.click(screen.getByRole("button", { name: "写投稿经验" }));
    const form = screen.getByRole("form", { name: "写投稿经验" });
    for (const [label, value] of [
      [/^投稿日期/, "2026-03-02"],
      [/^首轮决定日期/, "2026-04-13"],
    ] as const) {
      const input = within(form).getByLabelText(label);
      fireEvent.change(input, { target: { value } });
      fireEvent.focusOut(input);
    }
    await user.click(
      within(within(form).getByRole("radiogroup", { name: /首轮结果/ })).getByRole("radio", {
        name: "录用",
      }),
    );
    await user.selectOptions(within(form).getByLabelText(/^文章类型/), "REVIEW");
    await user.click(within(form).getByRole("button", { name: "发布投稿经验" }));
    await waitFor(() =>
      expect(screen.queryByRole("form", { name: "写投稿经验" })).not.toBeInTheDocument(),
    );
    expect(toastSuccess).toHaveBeenCalledWith(
      "投稿经验已发布",
      expect.objectContaining({ action: expect.objectContaining({ label: "查看" }) }),
    );
    await waitFor(() =>
      expect(screen.getByRole("article", { name: "同行经验" })).toHaveTextContent("16 条经验"),
    );
    const urls = fetchMock.mock.calls.map(([input]) => String(input));
    expect(urls).toContain("/api/journals/100/posts/summary?window=3y");
    expect(urls).toContain("/api/journals/100/posts?sort=latest");
  });
});

describe("列表", () => {
  it("加载更多投稿经验带游标", async () => {
    fetchMock.mockResolvedValue(Response.json(page([makeExperience({ postId: "10" })])));
    const user = userEvent.setup();
    renderArea({ seed: seed(makeSummary(), page([makeExperience()], "c2")) });
    await user.click(screen.getByRole("button", { name: "加载更多投稿经验" }));
    await waitFor(() => expect(screen.getAllByRole("link", { name: /首轮/ })).toHaveLength(2));
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "/api/journals/100/posts?sort=latest&cursor=c2",
    );
  });

  it("exp.list.empty：写第一条投稿经验", async () => {
    const user = userEvent.setup();
    renderArea({ seed: seed(makeFewSummary(0, 0), page([])) });
    expect(screen.getByText("还没有人分享这本期刊的投稿经验")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "写第一条投稿经验" }));
    expect(screen.getByRole("form", { name: "写投稿经验" })).toBeInTheDocument();
  });
});

describe("降级（spec 6.6）", () => {
  it("两份首屏都没有且取不到：暂不可用，没有按钮和列表", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    renderArea({ seed: () => {} });
    expect(await screen.findByText(/同行经验暂不可用/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "写投稿经验" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /首轮/ })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "投稿经验 · 审稿周期" })).toBeInTheDocument();
  });

  it("重试重取两份", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const user = userEvent.setup();
    renderArea({ seed: () => {} });
    const retry = await screen.findByRole("button", { name: "重试" });
    fetchMock.mockImplementation(async (input) =>
      String(input).includes("summary") ? Response.json(makeSummary()) : Response.json(page()),
    );
    await user.click(retry);
    expect(await screen.findByRole("article", { name: "同行经验" })).toHaveTextContent("42");
  });
});

describe("切到「全部」（final review I3）", () => {
  it("取「全部」时先留着近 3 年的数据，控件不消失", async () => {
    let release: (res: Response) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise((resolve) => {
        release = resolve;
      }),
    );
    const user = userEvent.setup();
    renderArea();
    await user.click(screen.getByRole("button", { name: "全部" }));
    expect(screen.getByRole("button", { name: "全部" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("article", { name: "同行经验" })).toHaveTextContent("42");
    release(Response.json(makeSummary({ window: "all", count: 20 })));
    await waitFor(() =>
      expect(screen.getByRole("article", { name: "同行经验" })).toHaveTextContent(
        "20 条经验 · 全部",
      ),
    );
  });

  it("「全部」取不到：只在卡里提示，按钮与列表照常，能切回近 3 年", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const user = userEvent.setup();
    renderArea();
    await user.click(screen.getByRole("button", { name: "全部" }));
    expect(await screen.findByText(/同行经验暂不可用/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "写投稿经验" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /首轮/ })).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "近 3 年" }));
    expect(screen.getByRole("article", { name: "同行经验" })).toHaveTextContent("42");
  });
});

describe("工具行与排序（简报 v0.15 §3.8）", () => {
  const two = [makeExperience(), makeExperience({ postId: "10" })];

  it("总条数取第一页的 total，两项排序", () => {
    renderArea({ seed: seed(makeSummary(), { ...page(two, "c2"), total: 18 }) });
    expect(screen.getByText("18 条投稿经验")).toBeVisible();
    const group = screen.getByRole("group", { name: "排序" });
    expect(
      within(group)
        .getAllByRole("button")
        .map((button) => button.textContent),
    ).toEqual(["最新投稿", "最有帮助"]);
    expect(within(group).getByRole("button", { name: "最新投稿" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("只有 1 条：有总条数、没有排序", () => {
    renderArea({ seed: seed(makeSummary(), page([makeExperience()])) });
    expect(screen.getByText("1 条投稿经验")).toBeVisible();
    expect(screen.queryByRole("group", { name: "排序" })).toBeNull();
  });

  it("没有经验：整行不出", () => {
    renderArea({ seed: seed(makeFewSummary(0, 0), page([])) });
    expect(screen.queryByText(/^[\d,]+ 条投稿经验$/)).toBeNull();
  });

  it("地址 sort=helpful：选中最有帮助，取这种排序", () => {
    params = new URLSearchParams("sort=helpful");
    renderArea({
      seed: (client) => {
        client.setQueryData(queryKeys.experienceSummary("100", "3y"), makeSummary());
        client.setQueryData(queryKeys.experienceList("100", "helpful"), {
          pages: [{ ...page(two), total: 2 }],
          pageParams: [null],
        });
      },
    });
    expect(screen.getByRole("button", { name: "最有帮助" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("点最有帮助：写进地址、清掉这种排序已缓存的页，从第一页取，取回前旧列表还在", async () => {
    const replace = vi.spyOn(window.history, "replaceState");
    fetchMock.mockReturnValue(new Promise(() => {}));
    const view = renderArea({
      seed: (client) => {
        seed(makeSummary(), { ...page(two, "c2"), total: 18 })(client);
        client.setQueryData(queryKeys.experienceList("100", "helpful"), {
          pages: [page(two, "h2"), page([makeExperience({ postId: "11" })])],
          pageParams: [null, "h2"],
        });
      },
    });
    await userEvent.setup().click(screen.getByRole("button", { name: "最有帮助" }));
    expect(replace).toHaveBeenLastCalledWith(null, "", "/journals/100?sort=helpful");
    expect(view.client.getQueryData(queryKeys.experienceList("100", "helpful"))).toBeUndefined();
    params = new URLSearchParams("sort=helpful");
    view.rerender(
      <ExperienceArea
        journalId="100"
        journalTitle="Journal of the American Heart Association"
        viewer={VERIFIED}
        now={NOW}
      />,
    );
    await waitFor(() =>
      expect(fetchMock.mock.calls.map(([input]) => String(input))).toContain(
        "/api/journals/100/posts?sort=helpful",
      ),
    );
    expect(screen.getAllByRole("link", { name: /首轮/ })).toHaveLength(2);
  });

  it("两页里有同一条经验只渲染一次", () => {
    renderArea({
      seed: (client) => {
        client.setQueryData(queryKeys.experienceSummary("100", "3y"), makeSummary());
        client.setQueryData(queryKeys.experienceList("100", "latest"), {
          pages: [page(two, "c2"), page([makeExperience({ postId: "10" })])],
          pageParams: [null, "c2"],
        });
      },
    });
    expect(screen.getAllByRole("link", { name: /首轮/ })).toHaveLength(2);
  });
});

describe("换排序失败（评审补充）", () => {
  it("只在列表就地提示，工具行、写投稿经验与另一种排序都还在", async () => {
    const two = [makeExperience(), makeExperience({ postId: "10" })];
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const element = () => (
      <ExperienceArea
        journalId="100"
        journalTitle="Journal of the American Heart Association"
        viewer={VERIFIED}
        now={NOW}
      />
    );
    const view = renderArea({ seed: seed(makeSummary(), { ...page(two), total: 18 }) });
    await userEvent.setup().click(screen.getByRole("button", { name: "最有帮助" }));
    params = new URLSearchParams("sort=helpful");
    view.rerender(element());
    expect(await screen.findByRole("alert")).toHaveTextContent("没有加载成功，请稍后再试");
    expect(screen.getByRole("button", { name: "写投稿经验" })).toBeVisible();
    expect(screen.getByText("18 条投稿经验")).toBeVisible();
    expect(screen.getByRole("button", { name: "最有帮助" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "重试" })).toBeVisible();
    // 切回最新投稿：从第一页重新取，这次取到了
    fetchMock.mockResolvedValue(Response.json({ ...page(two), total: 18 }));
    await userEvent.setup().click(screen.getByRole("button", { name: "最新投稿" }));
    params = new URLSearchParams();
    view.rerender(element());
    expect(await screen.findAllByRole("link", { name: /首轮/ })).toHaveLength(2);
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
