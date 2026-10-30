import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeCard } from "@tests/fixtures/discussion";
import { makeDetail } from "@tests/fixtures/experience";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PostView } from "@/components/experience/PostView";
import { deleteExperience, editExperience } from "@/lib/interaction/experience-actions";
import type { Experience, PostDetail } from "@/lib/interaction/experience-schema";
import { setHelpful } from "@/lib/interaction/helpful-actions";
import { queryKeys } from "@/lib/query-keys";

const toastSuccess = vi.fn();
vi.mock("sonner", () => ({ toast: { success: (...args: unknown[]) => toastSuccess(...args) } }));
vi.mock("@/lib/interaction/experience-actions", () => ({
  publishExperience: vi.fn(),
  editExperience: vi.fn(),
  deleteExperience: vi.fn(),
}));
vi.mock("@/lib/interaction/helpful-actions", () => ({ setHelpful: vi.fn() }));
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/posts/9",
  useSearchParams: () => new URLSearchParams(),
}));

const NOW = Date.parse("2026-10-28T02:00:00Z");
const remove = vi.mocked(deleteExperience);
const edit = vi.mocked(editExperience);

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function renderPost(
  overrides: Partial<Experience> = {},
  journal: Partial<PostDetail["journal"]> = {},
) {
  const detail = makeDetail(overrides);
  return renderDiscussion(
    <PostView
      detail={{ ...detail, journal: { ...detail.journal, ...journal } }}
      shareUrl="http://patra.test/posts/9"
    />,
  );
}

const main = () => screen.getByRole("article");
const profileLinks = () =>
  within(main())
    .queryAllByRole("link")
    .filter((link) => link.getAttribute("href")?.startsWith("/u/"));

describe("署名的几种视角（简报 §3.16、§3.20）", () => {
  it("别人看实名经验：昵称链主页，只有复制链接", async () => {
    const user = userEvent.setup();
    renderPost();
    expect(within(main()).getByRole("link", { name: "陈宇" })).toHaveAttribute(
      "href",
      "/u/chen-yu",
    );
    expect(main()).toHaveTextContent("@chen-yu");
    expect(main()).toHaveTextContent("发布于 2026 年 6 月 3 日");
    expect(screen.queryByRole("button", { name: "编辑" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "投稿经验操作" }));
    expect(await screen.findByRole("menuitem", { name: "复制链接" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "删除" })).not.toBeInTheDocument();
  });

  it("post.anonymous：别人看匿名经验，没有任何主页链接", () => {
    renderPost({ anonymous: true, author: null });
    expect(main()).toHaveTextContent("匿名作者");
    expect(profileLinks()).toHaveLength(0);
    expect(main()).not.toHaveTextContent("@");
  });

  it("post.anonymous.self：本人看自己的匿名经验", () => {
    renderPost({ anonymous: true, mine: true });
    expect(main()).toHaveTextContent("匿名作者（你）");
    expect(main()).toHaveTextContent("匿名发布 · 只有你知道是你写的");
    expect(profileLinks()).toHaveLength(0);
  });

  it("post.deleted-user：已注销用户", () => {
    renderPost({ author: makeCard({ state: "DELETED", displayName: null, username: null }) });
    expect(main()).toHaveTextContent("已注销用户");
    expect(profileLinks()).toHaveLength(0);
  });

  it("主页不可见的作者：只有昵称，不链接（Review Focus 4）", () => {
    renderPost({ author: makeCard({ username: null, profileVisible: false }) });
    expect(main()).toHaveTextContent("陈宇");
    expect(profileLinks()).toHaveLength(0);
    expect(main()).not.toHaveTextContent("/u/null");
  });

  it("已编辑是按钮，打开修改历史", () => {
    renderPost({ edited: true });
    expect(screen.getByRole("button", { name: "已编辑" })).toBeInTheDocument();
  });
});

describe("正文与期刊", () => {
  it("期刊名链期刊页的经验区", () => {
    renderPost();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Journal of the American Heart Association",
    );
    expect(within(screen.getByRole("heading", { level: 1 })).getByRole("link")).toHaveAttribute(
      "href",
      "/journals/100#experiences",
    );
  });

  it("期刊已失效：期刊名是纯文字（Review Focus 4）", () => {
    renderPost({}, { state: "DELETED" });
    expect(within(screen.getByRole("heading", { level: 1 })).queryByRole("link")).toBeNull();
  });

  it("没有正文时不渲染正文块", () => {
    renderPost({ body: null });
    expect(main().querySelector("[data-post-body]")).toBeNull();
  });
});

describe("本人的操作", () => {
  it("编辑：表单替换展示区，保存后回到展示态并刷新", async () => {
    edit.mockResolvedValue({
      ok: true,
      detail: makeDetail({ mine: true, firstDecision: "MINOR_REVISION" }),
    });
    const user = userEvent.setup();
    const { router } = renderPost({ mine: true });
    await user.click(screen.getByRole("button", { name: "编辑" }));
    expect(screen.getByRole("form", { name: "编辑投稿经验" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "投稿时间线" })).not.toBeInTheDocument();
    await user.click(
      within(screen.getByRole("radiogroup", { name: /首轮结果/ })).getByRole("radio", {
        name: "小修",
      }),
    );
    await user.click(
      within(screen.getByRole("radiogroup", { name: /最终结果/ })).getByRole("radio", {
        name: "录用",
      }),
    );
    await user.click(screen.getByRole("button", { name: "保存修改" }));
    await waitFor(() =>
      expect(screen.getByRole("list", { name: "投稿时间线" })).toBeInTheDocument(),
    );
    expect(screen.getByRole("list", { name: "投稿时间线" })).toHaveTextContent("首轮：小修");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("删除：确认后跳回期刊页经验区并 toast", async () => {
    remove.mockResolvedValue({ ok: true });
    const user = userEvent.setup();
    const { router } = renderPost({ mine: true });
    await user.click(screen.getByRole("button", { name: "投稿经验操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "删除" }));
    const dialog = await screen.findByRole("dialog", { name: "删除这条投稿经验？" });
    expect(dialog).toHaveTextContent(
      "删除后，它会从期刊页的列表和统计中去掉，下面的回复也不再显示。不能恢复。",
    );
    await user.click(within(dialog).getByRole("button", { name: "删除投稿经验" }));
    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/journals/100#experiences"));
    expect(toastSuccess).toHaveBeenCalledWith("投稿经验已删除");
  });

  it("期刊已失效时删除后去期刊列表", async () => {
    remove.mockResolvedValue({ ok: true });
    const user = userEvent.setup();
    const { router } = renderPost({ mine: true }, { state: "DELETED" });
    await user.click(screen.getByRole("button", { name: "投稿经验操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "删除" }));
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: "删除投稿经验" }),
    );
    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/journals"));
  });

  it("删除失败：对话框不关，就地提示", async () => {
    remove.mockResolvedValue({ ok: false, reason: "unavailable" });
    const user = userEvent.setup();
    renderPost({ mine: true });
    await user.click(screen.getByRole("button", { name: "投稿经验操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "删除" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "删除投稿经验" }));
    expect(await within(dialog).findByText("没有删除成功，请稍后再试")).toBeInTheDocument();
  });
});

describe("身份在页面上变化（final review I2）", () => {
  it("另一个标签页退出登录后刷新页面，不留上一位的「编辑」", async () => {
    const { client, router } = renderPost({ mine: true });
    expect(router.refresh).not.toHaveBeenCalled();
    act(() => client.setQueryData(queryKeys.currentUser, null));
    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
  });

  it("删除时会话已失效：去登录", async () => {
    remove.mockResolvedValue({ ok: false, reason: "signed-out" });
    const user = userEvent.setup();
    const { router } = renderPost({ mine: true });
    await user.click(screen.getByRole("button", { name: "投稿经验操作" }));
    await user.click(await screen.findByRole("menuitem", { name: "删除" }));
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: "删除投稿经验" }),
    );
    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/login?next=%2Fposts%2F9"));
  });
});

describe("经验页的有帮助（简报 v0.15 §3.10）", () => {
  const pill = () => within(main()).getByRole("button", { name: /^有帮助/ });

  it("别人的经验：正文之后的胶囊按钮", () => {
    renderPost({ helpfulCount: 5, helpful: true });
    expect(pill()).toHaveAccessibleName("有帮助，5 人觉得有帮助");
    expect(pill()).toHaveAttribute("aria-pressed", "true");
    const body = main().querySelector("[data-post-body]") as HTMLElement;
    expect(body.compareDocumentPosition(pill()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("自己的经验：只有人数；为 0 时没有", () => {
    const view = renderPost({ mine: true, helpfulCount: 5 });
    expect(within(main()).queryByRole("button", { name: /^有帮助/ })).toBeNull();
    expect(main()).toHaveTextContent("5 人觉得有帮助");
    view.unmount();
    renderPost({ mine: true, helpfulCount: 0 });
    expect(main()).not.toHaveTextContent("有帮助");
  });

  it("编辑时没有", async () => {
    renderPost({ mine: true, helpfulCount: 2 });
    await userEvent.setup().click(screen.getByRole("button", { name: "编辑" }));
    expect(main()).not.toHaveTextContent("人觉得有帮助");
  });

  it("成功后显示返回值（页面数据不变）", async () => {
    vi.mocked(setHelpful).mockResolvedValue({
      ok: true,
      result: { helpful: true, helpfulCount: 1 },
    });
    renderPost();
    await userEvent.setup().click(pill());
    await waitFor(() => expect(pill()).toHaveAccessibleName("有帮助，1 人觉得有帮助"));
  });

  it("这条已被删除：提示之后刷新页面", async () => {
    vi.useRealTimers();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.mocked(setHelpful).mockResolvedValue({ ok: false, reason: "gone" });
    const { router } = renderPost();
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(pill());
    expect(await screen.findByText("这条投稿经验已被删除")).toBeVisible();
    expect(router.refresh).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(4100));
    expect(router.refresh).toHaveBeenCalled();
  });
});
