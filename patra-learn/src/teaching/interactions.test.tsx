// @vitest-environment jsdom
// 参数滑块与开关、对比切换、强调块、排查路径、决策选项、掌握度：按画板与条目页的状态走一遍
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Callout } from "./callout";
import { Compare } from "./compare";
import { DebugTrail } from "./debug-trail";
import { DecisionOptions } from "./decision-options";
import { Mastery } from "./mastery";
import { Params } from "./params";
import { act, fireEvent, render, screen } from "./testing/render";

describe("Params", () => {
  const props = {
    title: "生产者收到了什么",
    controls: [
      { type: "switch" as const, id: "bound", label: "有队列", default: false },
      {
        type: "slider" as const,
        id: "interval",
        label: "补投间隔",
        min: 10,
        max: 300,
        step: 10,
        default: 60,
        unit: "秒",
      },
    ],
    cases: [
      {
        when: { bound: true },
        results: [{ label: "登记行", value: "删除", tone: "ok" as const }],
        verdict: { tone: "ok" as const, head: "正常。", text: "有队列接。" },
      },
      {
        results: [
          {
            label: "生产者依次收到",
            value: [
              { text: "退回", tone: "wait" as const },
              { text: "ack", tone: "ok" as const },
            ],
          },
        ],
        verdict: { tone: "bad" as const, head: "发不出去。", text: "每 {interval} 重发一次。" },
      },
    ],
  };

  it("拨开关、拖滑块，结果与结论实时换", () => {
    render(<Params {...props} />);
    expect(screen.getByText("发不出去。")).toBeTruthy();
    expect(screen.getByText("每 60 秒 重发一次。")).toBeTruthy();
    expect(screen.getByText("然后")).toBeTruthy();
    fireEvent.change(screen.getByRole("slider", { name: "补投间隔" }), {
      target: { value: "120" },
    });
    expect(screen.getByText("每 120 秒 重发一次。")).toBeTruthy();
    expect(screen.getByText("120 秒")).toBeTruthy();
    fireEvent.click(screen.getByRole("switch", { name: "有队列" }));
    expect(screen.getByText("正常。")).toBeTruthy();
    expect(screen.getByRole("switch", { name: "有队列" }).getAttribute("aria-checked")).toBe(
      "true",
    );
  });

  it("先预测：答完或跳过之前控件锁住", () => {
    render(
      <Params
        {...props}
        guess={{
          question: "会收到什么？",
          options: ["只有 ack", "先退回再 ack"],
          answer: "B",
          explain: "先退回。",
        }}
      />,
    );
    expect(screen.getByRole("switch", { name: "有队列" }).hasAttribute("disabled")).toBe(true);
    expect(screen.queryByText("发不出去。")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /只有 ack/ }));
    expect(screen.getByText("先退回。")).toBeTruthy();
    expect(screen.getByRole("switch", { name: "有队列" }).hasAttribute("disabled")).toBe(false);
    expect(screen.getByText("发不出去。")).toBeTruthy();
  });

  it("没有 case 匹配时说明这个组合没写结果", () => {
    render(<Params {...props} cases={[props.cases[0] as (typeof props.cases)[0]]} />);
    expect(screen.getByText("这个组合没有写结果。")).toBeTruthy();
  });
});

describe("Compare", () => {
  const variant = (label: string, bad: string) => ({
    label,
    steps: ["写评论", "提交事务", "发消息"],
    results: [{ label: "提醒", value: "发出了", tone: "ok" as const }],
    verdict: { tone: "ok" as const, head: "两样都成功。", text: "顺利时没区别。" },
    failure: {
      after: 2,
      results: [{ label: "提醒", value: bad, tone: "bad" as const }],
      verdict: { tone: "bad" as const, head: `${label}崩了。`, text: "出事了。" },
    },
  });

  it("切换写法；打开崩溃开关后，崩溃点之后的步骤划掉，结果换成崩溃时的", () => {
    render(
      <Compare
        title="三种写法"
        failureLabel="在第 2、3 步之间崩溃"
        variants={[variant("甲", "没有"), variant("乙", "指向空气")]}
      />,
    );
    expect(screen.getByText("两样都成功。")).toBeTruthy();
    fireEvent.click(screen.getByRole("tab", { name: "乙" }));
    expect(screen.getByRole("tab", { name: "乙" }).getAttribute("aria-selected")).toBe("true");
    fireEvent.click(screen.getByRole("switch", { name: "在第 2、3 步之间崩溃" }));
    expect(screen.getByText("乙崩了。")).toBeTruthy();
    expect(screen.getByText("进程崩在这里")).toBeTruthy();
    expect(screen.getByText("发消息").closest("[data-state]")?.getAttribute("data-state")).toBe(
      "lost",
    );
    expect(screen.getByText("指向空气")).toBeTruthy();
  });

  it("没写 failureLabel 时没有开关", () => {
    const plain = (label: string) => ({ ...variant(label, "x"), failure: undefined });
    render(<Compare title="比" variants={[plain("甲"), plain("乙")]} />);
    expect(screen.queryByRole("switch")).toBeNull();
  });
});

describe("Callout", () => {
  it("按 kind 给默认标题，label 可以改", () => {
    render(
      <>
        <Callout kind="analogy">
          <p>像挂号信。</p>
        </Callout>
        <Callout kind="lead" label="结论先说">
          <p>是 fake-ip。</p>
        </Callout>
      </>,
    );
    expect(screen.getByText("打个比方")).toBeTruthy();
    expect(screen.getByText("结论先说")).toBeTruthy();
    expect(screen.getByText("像挂号信。")).toBeTruthy();
  });
});

describe("DebugTrail", () => {
  const steps = [
    { hypothesis: "服务慢？", result: "本机很快。", verdict: "ruled-out" as const },
    { hypothesis: "走了代理？", result: "走代理 6 秒。", verdict: "closer" as const },
    { hypothesis: "解析成了什么？", result: "假地址。", verdict: "found" as const },
  ];

  it("先读假设，点「测一下，看结果」才展开，下一步随后出现", () => {
    render(<DebugTrail steps={steps} />);
    expect(screen.getByText("服务慢？")).toBeTruthy();
    expect(screen.queryByText("本机很快。")).toBeNull();
    expect(screen.queryByText("走了代理？")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "测一下，看结果" }));
    expect(screen.getByText("本机很快。")).toBeTruthy();
    expect(
      screen.getByText("服务慢？").closest("[data-verdict]")?.getAttribute("data-verdict"),
    ).toBe("ruled-out");
    expect(screen.getByText("走了代理？")).toBeTruthy();
  });

  it("全部展开", () => {
    render(<DebugTrail steps={steps} />);
    fireEvent.click(screen.getByRole("button", { name: "全部展开" }));
    expect(screen.getByText("假地址。")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "测一下，看结果" })).toBeNull();
  });
});

describe("DecisionOptions", () => {
  const options = [
    {
      name: "签发方独占",
      summary: "identity 管。",
      pros: "规则一处。",
      cons: "多一次调用。",
      chosen: true,
    },
    { name: "网关自持", summary: "网关存。", pros: "少一次调用。", cons: "规则散了。" },
  ];

  it("默认展开选中的那个，点别的看它的好处与代价", () => {
    render(<DecisionOptions options={options} />);
    expect(screen.getByText("选了这个")).toBeTruthy();
    expect(screen.getByText("规则一处。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /网关自持/ }));
    expect(screen.getByText("规则散了。")).toBeTruthy();
    expect(screen.queryByText("规则一处。")).toBeNull();
  });
});

describe("Mastery", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("受控：选一档回调给页面，提示 2.6 秒后消失，可取消标记", () => {
    const onChange = vi.fn();
    const { rerender } = render(<Mastery value={0} onChange={onChange} />);
    expect(screen.getByText("只记在这个浏览器里，不用登录")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /理解中/ }));
    expect(onChange).toHaveBeenCalledWith(2);
    rerender(<Mastery value={2} onChange={onChange} />);
    expect(screen.getByText("记下了：理解中")).toBeTruthy();
    act(() => vi.advanceTimersByTime(2600));
    expect(screen.queryByText("记下了：理解中")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "取消标记" }));
    expect(onChange).toHaveBeenLastCalledWith(0);
  });

  it("上次标过：页头写出上次标的是哪一档", () => {
    render(<Mastery value={1} lastMarked="10/26" onChange={() => {}} />);
    expect(screen.getByText("10/26 你标的是「刚接触」")).toBeTruthy();
    expect(screen.getByRole("button", { name: /刚接触/ }).getAttribute("aria-pressed")).toBe(
      "true",
    );
  });
});
