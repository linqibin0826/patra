import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeDetail, makeExperience } from "@tests/fixtures/experience";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceForm } from "@/components/experience/ExperienceForm";
import {
  type ExperienceResult,
  editExperience,
  publishExperience,
} from "@/lib/interaction/experience-actions";

vi.mock("@/lib/interaction/experience-actions", () => ({
  publishExperience: vi.fn(),
  editExperience: vi.fn(),
  deleteExperience: vi.fn(),
}));
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/journals/100",
  useSearchParams: () => new URLSearchParams(),
}));

const publish = vi.mocked(publishExperience);
const edit = vi.mocked(editExperience);

beforeEach(() => {
  // 只假 Date：北京时间 2026-10-28 10:00
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-10-28T02:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function renderCreate() {
  const onDone = vi.fn();
  const onCancel = vi.fn();
  const view = renderDiscussion(
    <ExperienceForm
      mode="create"
      journalId="100"
      journalTitle="Journal of the American Heart Association"
      onDone={onDone}
      onCancel={onCancel}
    />,
  );
  return { ...view, onDone, onCancel };
}

function renderEdit(anonymous = false) {
  const onDone = vi.fn();
  const onCancel = vi.fn();
  const view = renderDiscussion(
    <ExperienceForm
      mode="edit"
      postId="9"
      journalTitle="Journal of the American Heart Association"
      initial={makeExperience({ anonymous })}
      anonymous={anonymous}
      onDone={onDone}
      onCancel={onCancel}
    />,
  );
  return { ...view, onDone, onCancel };
}

const dateField = (name: RegExp) => screen.getByLabelText(name);
const group = (name: RegExp) => screen.getByRole("radiogroup", { name });
const radio = (groupName: RegExp, option: string) =>
  within(group(groupName)).getByRole("radio", { name: option });

/// 文章类型：点开下拉再点选项
async function chooseType(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(screen.getByRole("combobox", { name: /^文章类型/ }));
  await user.click(await screen.findByRole("option", { name: label }));
}

function setDate(name: RegExp, value: string) {
  const input = dateField(name);
  fireEvent.change(input, { target: { value } });
  // React 的 onBlur 监听的是 focusout
  fireEvent.focusOut(input);
}

/// 填好全部必填：3 月 2 日投稿、4 月 13 日首轮大修、录用、原创研究
async function fillRequired(user: ReturnType<typeof userEvent.setup>) {
  setDate(/^投稿日期/, "2026-03-02");
  setDate(/^首轮决定日期/, "2026-04-13");
  await user.click(radio(/首轮结果/, "大修"));
  await user.click(radio(/最终结果/, "录用"));
  await chooseType(user, "原创研究");
}

describe("新建：初始状态（简报 §3.15）", () => {
  it("标题、必填说明、焦点在投稿日期、文章类型 6 种", async () => {
    const user = userEvent.setup();
    renderCreate();
    expect(screen.getByRole("heading", { name: "写投稿经验" })).toBeInTheDocument();
    expect(screen.getByText(/带 \* 的必填/)).toBeInTheDocument();
    expect(dateField(/^投稿日期/)).toHaveFocus();
    const type = screen.getByRole("combobox", { name: /^文章类型/ });
    expect(type).toHaveTextContent("请选择");
    await user.click(type);
    const options = within(await screen.findByRole("listbox")).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "原创研究",
      "综述",
      "系统综述 / Meta 分析",
      "病例报告",
      "短篇通讯",
      "其他",
    ]);
  });

  it("匿名发布带不可更改的提示", () => {
    renderCreate();
    expect(screen.getByRole("checkbox", { name: /匿名发布/ })).not.toBeChecked();
    expect(screen.getByText("发布后不能更改匿名设置。")).toBeInTheDocument();
  });
});

describe("一致性联动（spec 6.3）", () => {
  it("直接拒稿：最终自动选拒稿、其余禁用，最终日期、审稿轮数、审稿质量禁用", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/首轮结果/, "直接拒稿"));
    expect(radio(/最终结果/, "拒稿")).toBeChecked();
    for (const option of ["录用", "撤稿", "进行中"]) {
      expect(radio(/最终结果/, option)).toBeDisabled();
    }
    expect(dateField(/^最终决定日期/)).toBeDisabled();
    expect(screen.getByLabelText(/^审稿轮数/)).toBeDisabled();
    for (const option of ["1", "2", "3", "4", "5"]) {
      expect(radio(/审稿质量/, option)).toBeDisabled();
    }
    // 审稿轮数与审稿质量下面各一句
    expect(screen.getAllByText("没有送审，不用填审稿轮数和审稿质量")).toHaveLength(2);
    expect(screen.getByText("首轮已是最终结果，不用填最终决定日期")).toBeInTheDocument();
  });

  it("从直接拒稿改回大修：最终结果都能选且未选，审稿轮数清空", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/首轮结果/, "直接拒稿"));
    await user.click(radio(/首轮结果/, "大修"));
    for (const option of ["录用", "拒稿", "撤稿", "进行中"]) {
      expect(radio(/最终结果/, option)).toBeEnabled();
      expect(radio(/最终结果/, option)).not.toBeChecked();
    }
    expect(screen.getByLabelText(/^审稿轮数/)).toHaveValue("");
  });

  it("审后拒稿：审稿轮数固定为 1", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/首轮结果/, "审后拒稿"));
    expect(screen.getByLabelText(/^审稿轮数/)).toHaveValue("1");
    expect(screen.getByLabelText(/^审稿轮数/)).toBeDisabled();
    expect(screen.getByText("首轮即结束，审稿只有 1 轮")).toBeInTheDocument();
  });

  it("首轮录用：最终自动选录用", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/首轮结果/, "录用"));
    expect(radio(/最终结果/, "录用")).toBeChecked();
    expect(radio(/最终结果/, "拒稿")).toBeDisabled();
  });

  it("大修且进行中：最终决定日期禁用并清空", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/首轮结果/, "大修"));
    setDate(/^最终决定日期/, "2026-06-01");
    await user.click(radio(/最终结果/, "进行中"));
    expect(dateField(/^最终决定日期/)).toBeDisabled();
    expect(dateField(/^最终决定日期/)).toHaveValue("");
    expect(screen.getByText("稿件还在进行中，不用填最终决定日期")).toBeInTheDocument();
  });
});

describe("校验", () => {
  it("失焦时校验这个字段", async () => {
    renderCreate();
    fireEvent.focusOut(dateField(/^投稿日期/));
    expect(await screen.findByText("请填写投稿日期")).toBeInTheDocument();
    expect(screen.queryByText("请填写首轮决定日期")).not.toBeInTheDocument();
  });

  it("空表提交：五条必填文案，焦点回投稿日期", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    for (const text of [
      "请填写投稿日期",
      "请填写首轮决定日期",
      "请选择首轮结果",
      "请选择最终结果",
      "请选择文章类型",
    ]) {
      expect(await screen.findByText(text)).toBeInTheDocument();
    }
    expect(dateField(/^投稿日期/)).toHaveFocus();
    expect(publish).not.toHaveBeenCalled();
  });

  it("首轮决定早于投稿", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026-03-02");
    setDate(/^首轮决定日期/, "2026-03-01");
    expect(await screen.findByText("首轮决定日期不能早于投稿日期")).toBeInTheDocument();
  });

  it("投稿日期改晚后首轮日期报错，已填的值不清空", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026-03-02");
    setDate(/^首轮决定日期/, "2026-04-13");
    setDate(/^投稿日期/, "2026-05-01");
    fireEvent.focusOut(dateField(/^首轮决定日期/));
    expect(await screen.findByText("首轮决定日期不能早于投稿日期")).toBeInTheDocument();
    expect(dateField(/^首轮决定日期/)).toHaveValue("2026-04-13");
  });

  it("手敲的日期失焦后规范化", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026/3/2");
    expect(dateField(/^投稿日期/)).toHaveValue("2026-03-02");
  });

  it("认不出的日期报格式错误", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026/9/31");
    expect(await screen.findByText("日期写成 2026-08-14 这样")).toBeInTheDocument();
  });

  it("超出范围的日期：晚于今天、早于 1990 年", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026-10-29");
    expect(await screen.findByText("日期不能晚于今天")).toBeInTheDocument();
    setDate(/^投稿日期/, "1989-12-31");
    expect(await screen.findByText("日期不能早于 1990 年")).toBeInTheDocument();
  });

  it("两个日期都有效时算出用时，填了最终日期再算总时长", async () => {
    renderCreate();
    setDate(/^投稿日期/, "2026-03-02");
    setDate(/^首轮决定日期/, "2026-04-13");
    const form = screen.getByRole("form", { name: "写投稿经验" });
    await waitFor(() => expect(form).toHaveTextContent("首轮用时 42 天（由日期算出）"));
    setDate(/^最终决定日期/, "2026-06-01");
    await waitFor(() =>
      expect(form).toHaveTextContent("首轮用时 42 天 · 投稿到最终决定 91 天（由日期算出）"),
    );
  });

  it("正文超过 2,000 字时不能提交", async () => {
    const user = userEvent.setup();
    renderCreate();
    fireEvent.change(screen.getByRole("textbox", { name: "经历" }), {
      target: { value: "字".repeat(2001) },
    });
    expect(screen.getByRole("button", { name: "发布投稿经验" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(publish).not.toHaveBeenCalled();
  });
});

describe("选填单选可以取消", () => {
  it("再点一次取消，清除选择也能取消", async () => {
    const user = userEvent.setup();
    renderCreate();
    await user.click(radio(/审稿质量/, "4"));
    expect(radio(/审稿质量/, "4")).toBeChecked();
    await user.click(radio(/审稿质量/, "4"));
    expect(radio(/审稿质量/, "4")).not.toBeChecked();
    await user.click(radio(/是否付了 APC/, "付了"));
    await user.click(within(group(/是否付了 APC/)).getByRole("button", { name: "清除选择" }));
    expect(radio(/是否付了 APC/, "付了")).not.toBeChecked();
  });
});

describe("提交", () => {
  it("只填必填：选填全是 null、不匿名；提交中整张表单禁用；成功交给 onDone", async () => {
    let resolve: (result: ExperienceResult) => void = () => {};
    publish.mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const user = userEvent.setup();
    const { onDone } = renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(await screen.findByRole("button", { name: "发布中…" })).toBeInTheDocument();
    expect(dateField(/^投稿日期/)).toBeDisabled();
    expect(publish).toHaveBeenCalledWith("100", {
      submittedOn: "2026-03-02",
      firstDecisionOn: "2026-04-13",
      firstDecision: "MAJOR_REVISION",
      finalOutcome: "ACCEPTED",
      finalDecisionOn: null,
      articleType: "ORIGINAL_RESEARCH",
      reviewRounds: null,
      reviewQuality: null,
      editorCommunication: null,
      apcPaid: null,
      previouslyRejected: null,
      body: null,
      anonymous: false,
    });
    resolve({ ok: true, detail: makeDetail() });
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(makeDetail()));
  });

  it("服务端的一致性错误落在最终结果下（Review Focus 1）", async () => {
    publish.mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "finalOutcome", code: "INCONSISTENT" }],
    });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    const finalGroup = group(/最终结果/).closest("[data-field]") as HTMLElement;
    expect(
      await within(finalGroup).findByText(
        "首轮已是最终结果：直接拒稿、审后拒稿最终只能是拒稿，首轮录用只能是录用",
      ),
    ).toBeInTheDocument();
  });

  it.each([
    ["body", "INVALID_CHARACTER", "经历"],
    ["apcPaid", "INVALID", "是否付了 APC"],
    ["previouslyRejected", "INVALID", "是否转投过"],
  ])("服务端的 %s 错误显示出来（final review I1）", async (field, code) => {
    publish.mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field, code }],
    });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(await screen.findByText("这一项的内容不对，请重新填写")).toBeInTheDocument();
  });

  it("认不出的字段错误走底部提示条", async () => {
    publish.mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "somethingElse", code: "INVALID" }],
    });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(await screen.findByText("没有发布成功，请稍后再试。")).toBeInTheDocument();
  });

  it("重复投稿日期：底部提示带链接，焦点回投稿日期", async () => {
    publish.mockResolvedValue({ ok: false, reason: "duplicate", existingPostId: "77" });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(
      await screen.findByText(
        "你已经写过 2026 年 3 月 2 日投这本期刊的经验，同一天的投稿只能写一条。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看那条经验" })).toHaveAttribute("href", "/posts/77");
    expect(dateField(/^投稿日期/)).toHaveFocus();
  });

  it.each([
    [
      { ok: false, reason: "daily-limit", retryAfterSeconds: 60 },
      "今天已经发布了 10 条投稿经验，明天再来吧。",
    ],
    [{ ok: false, reason: "account-restricted" }, "账号状态异常，暂时不能发布。"],
    [{ ok: false, reason: "unavailable" }, "没有发布成功，请稍后再试。"],
  ] as const)("失败 %j", async (result, text) => {
    publish.mockResolvedValue(result);
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(dateField(/^投稿日期/)).toHaveValue("2026-03-02");
  });

  it("邮箱未验证：提示带重新发送验证邮件", async () => {
    publish.mockResolvedValue({ ok: false, reason: "email-unverified" });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(await screen.findByText("验证邮箱后才能写投稿经验。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });

  it("会话失效且已填：原地确认去登录", async () => {
    publish.mockResolvedValue({ ok: false, reason: "signed-out" });
    const user = userEvent.setup();
    renderCreate();
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "发布投稿经验" }));
    expect(
      await screen.findByText("登录已过期，去重新登录？已写的内容会丢失。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F100",
    );
  });
});

describe("取消", () => {
  it("没填直接收起", async () => {
    const user = userEvent.setup();
    const { onCancel } = renderCreate();
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("填了先确认：继续填写留下，放弃才收起", async () => {
    const user = userEvent.setup();
    const { onCancel } = renderCreate();
    setDate(/^投稿日期/, "2026-03-02");
    await user.click(screen.getByRole("button", { name: "取消" }));
    const dialog = await screen.findByRole("dialog", { name: "放弃这条投稿经验？" });
    expect(within(dialog).getByText("已经填写的内容不会保存。")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "继续填写" }));
    expect(onCancel).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "取消" }));
    await user.click(
      within(await screen.findByRole("dialog", { name: "放弃这条投稿经验？" })).getByRole(
        "button",
        { name: "放弃" },
      ),
    );
    expect(onCancel).toHaveBeenCalled();
  });
});

describe("编辑模式", () => {
  it("带原值、只读的发布方式、保存修改不带 anonymous", async () => {
    edit.mockResolvedValue({ ok: true, detail: makeDetail() });
    const user = userEvent.setup();
    const { onDone } = renderEdit();
    expect(screen.getByRole("heading", { name: "编辑投稿经验" })).toBeInTheDocument();
    expect(dateField(/^投稿日期/)).toHaveValue("2026-03-02");
    expect(radio(/首轮结果/, "大修")).toBeChecked();
    expect(screen.getByText("这条经验以你的名字发布，不能改成匿名。")).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /匿名发布/ })).not.toBeInTheDocument();
    await user.click(radio(/首轮结果/, "小修"));
    await user.click(radio(/最终结果/, "录用"));
    await user.click(screen.getByRole("button", { name: "保存修改" }));
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    const [postId, request] = edit.mock.calls[0] ?? [];
    expect(postId).toBe("9");
    expect(request).not.toHaveProperty("anonymous");
    expect(request).toMatchObject({ firstDecision: "MINOR_REVISION", reviewRounds: 2 });
  });

  it("匿名经验的只读说明", () => {
    renderEdit(true);
    expect(screen.getByText("这条经验以匿名发布，不能更改。")).toBeInTheDocument();
  });

  it("没改直接取消不弹确认", async () => {
    const user = userEvent.setup();
    const { onCancel } = renderEdit();
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(onCancel).toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
