import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EMPTY_PROFILE, MY_PROFILE, PUBLIC_PRIVACY } from "@tests/fixtures/profile";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProfileForm } from "@/components/settings/profile/ProfileForm";
import type { CurrentUser } from "@/lib/auth/types";
import { saveProfile } from "@/lib/identity/profile-actions";
import type { MyProfile, PrivacySettings } from "@/lib/identity/profile-schema";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/settings/profile",
}));
vi.mock("@/lib/identity/profile-actions", () => ({ saveProfile: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

function renderForm({
  profile = MY_PROFILE,
  user = USER,
  privacy = PUBLIC_PRIVACY,
}: {
  profile?: MyProfile;
  user?: CurrentUser;
  privacy?: PrivacySettings;
} = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(client, "invalidateQueries");
  render(
    <QueryClientProvider client={client}>
      <ProfileForm initial={profile} user={user} privacy={privacy} />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), client, invalidate };
}

const nickname = () => screen.getByLabelText("昵称");
const saveBar = () => screen.queryByRole("region", { name: "保存资料" });

beforeEach(() => {
  vi.mocked(saveProfile).mockResolvedValue({ ok: true, profile: MY_PROFILE });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("ProfileForm · 公开资料", () => {
  it("昵称占位是用户名，常驻提示；计数按字素", () => {
    renderForm();
    expect(nickname()).toHaveAttribute("placeholder", "chen-yu");
    expect(nickname()).toHaveAccessibleDescription(
      "2–30 个字。不填就显示用户名。昵称可以和别人重复，主页上会同时显示 @用户名",
    );
    expect(screen.getByText("2 / 30")).toBeInTheDocument();
  });

  it("简介计数：接近上限 amber，超出变危险色并显示错误", async () => {
    const { user } = renderForm({ profile: EMPTY_PROFILE });
    const bio = screen.getByLabelText(/简介/);
    await user.click(bio);
    await user.paste("字".repeat(170));
    expect(screen.getByText("170 / 200")).toHaveClass("text-amber-700");
    await user.paste("字".repeat(40));
    expect(screen.getByText("210 / 200")).toHaveClass("text-status-danger");
    expect(bio).toHaveAccessibleDescription("简介最多 200 字");
  });

  it("昵称表情太多超出字节上限：计数变危险色，立即提示删减", async () => {
    const { user } = renderForm({ profile: EMPTY_PROFILE });
    await user.click(nickname());
    await user.paste("👨‍👩‍👧".repeat(7));
    expect(screen.getByText("7 / 30")).toHaveClass("text-status-danger");
    expect(nickname()).toHaveAccessibleDescription("表情符号占用的位置较多，请删减一些");
  });

  it("链接：加到 5 条后「添加链接」换成说明；可以删除一行", async () => {
    const { user } = renderForm();
    for (let i = 0; i < 3; i += 1) {
      await user.click(screen.getByRole("button", { name: "添加链接" }));
    }
    expect(screen.queryByRole("button", { name: "添加链接" })).not.toBeInTheDocument();
    expect(screen.getByText("最多 5 条链接")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "删除链接 5" }));
    expect(screen.getAllByRole("textbox", { name: /的网址$/ })).toHaveLength(4);
  });

  it("邮箱未验证：链接区提示验证后才展示", () => {
    renderForm({ user: { ...USER, emailVerified: false } });
    expect(screen.getByText("验证邮箱后，链接才会展示在主页上")).toBeInTheDocument();
  });
});

describe("ProfileForm · 专业背景", () => {
  it("说明当前可见性并链到隐私页", () => {
    renderForm({ privacy: { ...PUBLIC_PRIVACY, professionalVisibility: "SELF" } });
    expect(screen.getByText(/当前：仅自己可见/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "更改" })).toHaveAttribute("href", "/settings/privacy");
  });

  it("身份下拉：不填写 + 八项，当前是医师", async () => {
    const { user } = renderForm();
    const select = screen.getByRole("combobox", { name: "身份" });
    expect(select).toHaveTextContent("医师");
    await user.click(select);
    expect(
      within(await screen.findByRole("listbox"))
        .getAllByRole("option")
        .map((option) => option.textContent),
    ).toEqual(["不填写", "医师", "护士", "药师", "医技", "科研人员", "学生", "行业从业者", "其他"]);
  });

  it("身份选「不填写」后保存的是空值（请求里由 toUpdateRequest 转成 null）", async () => {
    const { user } = renderForm();
    await user.click(screen.getByRole("combobox", { name: "身份" }));
    await user.click(await screen.findByRole("option", { name: "不填写" }));
    expect(screen.getByRole("combobox", { name: "身份" })).toHaveTextContent("不填写");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(saveProfile).toHaveBeenCalled());
    expect(vi.mocked(saveProfile).mock.calls[0]?.[0]).toMatchObject({ occupation: "" });
  });
});

describe("ProfileForm · 保存条", () => {
  it("没改动时不出现；改了出现；放弃恢复原值", async () => {
    const { user } = renderForm();
    expect(saveBar()).not.toBeInTheDocument();
    await user.clear(nickname());
    await user.type(nickname(), "小陈");
    expect(saveBar()).toHaveTextContent("有未保存的修改");
    await user.click(within(saveBar() as HTMLElement).getByRole("button", { name: "放弃" }));
    expect(nickname()).toHaveValue("陈宇");
    expect(saveBar()).not.toBeInTheDocument();
  });

  it("有未保存的修改时点「更改」（去隐私页）：先弹离开确认", async () => {
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("link", { name: "更改" }));
    expect(await screen.findByRole("dialog", { name: "有未保存的修改" })).toBeInTheDocument();
  });

  it("保存成功：提交表单值，提示「资料已保存」，保存条消失，刷新当前用户与页面", async () => {
    const { user, invalidate } = renderForm();
    await user.clear(nickname());
    await user.type(nickname(), "小陈");
    vi.mocked(saveProfile).mockResolvedValue({
      ok: true,
      profile: { ...MY_PROFILE, displayName: "小陈" },
    });
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith("资料已保存"));
    expect(vi.mocked(saveProfile).mock.calls[0]?.[0]).toMatchObject({ displayName: "小陈" });
    expect(saveBar()).not.toBeInTheDocument();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
    expect(router.refresh).toHaveBeenCalled();
    expect(nickname()).toHaveValue("小陈");
  });

  it("保存中：按钮「保存中…」，字段只读", async () => {
    vi.mocked(saveProfile).mockReturnValue(new Promise(() => {}));
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByRole("button", { name: "保存中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(nickname()).toHaveAttribute("readonly");
  });

  it("后端字段错误：显示在对应字段下，焦点移过去", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "links[1].url", code: "INVALID_FORMAT" }],
    });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    const url = screen.getByRole("textbox", { name: "链接 2 的网址" });
    await waitFor(() => expect(url).toHaveFocus());
    expect(url).toHaveAccessibleDescription("请输入以 http:// 或 https:// 开头的完整网址");
  });

  it("昵称是保留词（422）：昵称下方提示", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "displayName", code: "RESERVED" }],
    });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() =>
      expect(nickname()).toHaveAccessibleDescription("这个昵称不能使用，请换一个"),
    );
  });

  it.each([
    ["catalog-unavailable", "机构或主题词暂时无法校验，请稍后再保存。你填的内容都还在。"],
    ["conflict", "资料没有保存成功，请再保存一次"],
    ["unavailable", "没有保存成功，请稍后再试"],
  ] as const)("%s：保存条上方提示条，保留输入", async (reason, message) => {
    vi.mocked(saveProfile).mockResolvedValue({ ok: false, reason });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(nickname()).toHaveValue("陈宇!");
    expect(saveBar()).toBeInTheDocument();
  });

  it("前端校验不过：不请求，焦点到第一个出错字段", async () => {
    const { user } = renderForm();
    await user.clear(nickname());
    await user.type(nickname(), "陈");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(nickname()).toHaveFocus());
    expect(nickname()).toHaveAccessibleDescription("昵称至少 2 个字");
    expect(saveProfile).not.toHaveBeenCalled();
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(saveProfile).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(
        "/account/deletion-pending?next=%2Fsettings%2Fprofile",
      ),
    );
  });

  it("会话失效：当前用户置空，去登录页", async () => {
    vi.mocked(saveProfile).mockResolvedValue({ ok: false, reason: "signed-out" });
    const { user, client } = renderForm();
    await user.type(nickname(), "!");
    await act(async () => {
      await user.click(screen.getByRole("button", { name: "保存" }));
    });
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Fprofile"),
    );
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
  });
});

describe("ProfileForm · 机构", () => {
  it("回显已保存的 ROR 机构；机构已不在库中（422）时提示并聚焦「更换」", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "organization.rorId", code: "NOT_FOUND" }],
    });
    const { user } = renderForm();
    expect(screen.getByText("北京协和医院")).toBeInTheDocument();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(
      await screen.findByText("这个机构已不在机构库中，请重新选择或手动填写"),
    ).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "更换" })).toHaveFocus());
  });
});

describe("ProfileForm · 关注领域", () => {
  it("计数 n / 10；某个主题词已不可用（422）时那一项描红并说明", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "researchInterests[1]", code: "NOT_FOUND" }],
    });
    const { user } = renderForm();
    expect(screen.getByText("2 / 10")).toBeInTheDocument();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(
      await screen.findByText("Atrial Fibrillation：这个主题词已不可用，请移除"),
    ).toBeInTheDocument();
  });

  it("主题词的错误跟着词走：调整顺序后仍标在原来那个词上", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "researchInterests[1]", code: "NOT_FOUND" }],
    });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await screen.findByText("Atrial Fibrillation：这个主题词已不可用，请移除");
    screen.getByRole("button", { name: "调整 Heart Failure 的位置" }).focus();
    await user.keyboard("{Alt>}{ArrowRight}{/Alt}");
    expect(screen.getByText("Atrial Fibrillation：这个主题词已不可用，请移除")).toBeInTheDocument();
    expect(screen.queryByText(/^Heart Failure：/)).not.toBeInTheDocument();
  });

  it("移除出错的主题词：错误跟着消失；焦点在保存失败时移到搜索框", async () => {
    vi.mocked(saveProfile).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "researchInterests[1]", code: "NOT_FOUND" }],
    });
    const { user } = renderForm();
    await user.type(nickname(), "!");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await screen.findByText("Atrial Fibrillation：这个主题词已不可用，请移除");
    expect(screen.getByRole("combobox", { name: "搜索 MeSH 主题词" })).toHaveFocus();
    await user.click(screen.getByRole("button", { name: "移除 Atrial Fibrillation" }));
    expect(screen.queryByText(/这个主题词已不可用/)).not.toBeInTheDocument();
  });
});
