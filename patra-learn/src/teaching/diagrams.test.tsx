// @vitest-environment jsdom
// 结构图、时序图、状态机、逐步动画：按画板 C-Structure / C-Sequence / C-StateMachine / C-Stepper 的状态走一遍
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Sequence } from "./sequence";
import { StateMachine } from "./state-machine";
import { Stepper } from "./stepper";
import { Structure } from "./structure";
import { act, fireEvent, render, screen } from "./testing/render";

const caption = () => screen.getByTestId("caption").textContent;
const stateOf = (testId: string) => screen.getByTestId(testId).getAttribute("data-state");

describe("Structure", () => {
  const props = {
    title: "一条消息怎么找到它的队列",
    nodes: [
      { id: "producer", label: "interaction", sub: "生产者", col: 1, desc: "只管交给交换机。" },
      { id: "exchange", label: "interaction.events", code: true, col: 2, desc: "按路由键转发。" },
      { id: "inbox", label: "notification.inbox", code: true, col: 3, desc: "把消息存下来。" },
    ],
    edges: [
      { from: "producer", to: "exchange" },
      { from: "exchange", to: "inbox", label: "绑定", desc: "这个队列要接这些路由键。" },
    ],
    traces: [
      {
        button: "发一条",
        packet: "comment",
        hops: [
          { at: "producer", caption: "交给交换机。" },
          { at: "exchange", caption: "查绑定。" },
          { at: "inbox", caption: "进了队列。" },
        ],
      },
    ],
  };

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("默认：说明是引导语，方块都正常", () => {
    render(<Structure {...props} />);
    expect(caption()).toBe("点图上的方块或连线，看它管什么。");
    expect(stateOf("node-exchange")).toBe("normal");
  });

  it("点方块：它和相连的线点亮，其余变淡，说明换成它的职责", () => {
    render(<Structure {...props} />);
    fireEvent.click(screen.getByRole("button", { name: /interaction\.events/ }));
    expect(caption()).toContain("按路由键转发。");
    expect(stateOf("node-exchange")).toBe("on");
    expect(stateOf("node-inbox")).toBe("dim");
    expect(stateOf("edge-producer-exchange")).toBe("on");
  });

  it("点有说明的连线", () => {
    render(<Structure {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "绑定" }));
    expect(caption()).toContain("这个队列要接这些路由键。");
    expect(stateOf("node-inbox")).toBe("on");
    expect(stateOf("node-producer")).toBe("dim");
  });

  it("发一条：消息块一跳一跳走过去，每跳换一句说明", () => {
    render(<Structure {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "发一条" }));
    expect(caption()).toContain("交给交换机。");
    act(() => vi.advanceTimersByTime(800));
    expect(caption()).toContain("查绑定。");
    expect(stateOf("edge-producer-exchange")).toBe("on");
    act(() => vi.advanceTimersByTime(800));
    expect(caption()).toContain("进了队列。");
    expect(stateOf("node-inbox")).toBe("on");
    expect(screen.getByTestId("packet").textContent).toBe("comment");
  });
});

describe("Sequence", () => {
  const props = {
    title: "那 6 秒花在了哪",
    actors: [
      { id: "browser", label: "浏览器" },
      { id: "proxy", label: "代理" },
      { id: "mini", label: "mini" },
    ],
    messages: [
      { from: "browser", to: "proxy", label: "先连假地址", time: "2 ms", caption: "交给代理。" },
      {
        from: "proxy",
        to: "mini",
        label: "拨号没通",
        time: "6.0 s",
        bad: true,
        caption: "等到超时。",
      },
      { from: "browser", to: "mini", label: "直连", time: "6.06 s", caption: "60 毫秒回来。" },
    ],
    summary: { time: "6.06 s", caption: "6 秒都在等。" },
  };

  it("一条一条往下走，时钟跟着走，最后看总结再从头", () => {
    render(<Sequence {...props} />);
    expect(caption()).toBe("2 ms交给代理。");
    expect(stateOf("message-1")).toBe("dim");
    fireEvent.click(screen.getByRole("button", { name: "下一条" }));
    expect(caption()).toBe("6.0 s等到超时。");
    expect(stateOf("message-1")).toBe("bad");
    fireEvent.click(screen.getByRole("button", { name: "下一条" }));
    fireEvent.click(screen.getByRole("button", { name: "看总结" }));
    expect(caption()).toBe("6.06 s6 秒都在等。");
    expect(screen.getByText("全部 3 条")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "从头看" }));
    expect(screen.getByText("第 1 条 / 3")).toBeTruthy();
  });

  it("点图上任意一条消息跳过去，上一条退回", () => {
    render(<Sequence {...props} />);
    fireEvent.click(screen.getByRole("button", { name: /直连/ }));
    expect(caption()).toBe("6.06 s60 毫秒回来。");
    fireEvent.click(screen.getByRole("button", { name: "上一条" }));
    expect(caption()).toBe("6.0 s等到超时。");
  });
});

describe("StateMachine", () => {
  const props = {
    title: "一行登记的一生",
    subject: "event_publication",
    states: [
      { id: "in-tx", label: "事务中", col: 1, desc: "还没提交。" },
      { id: "pending", label: "待外送", col: 2, desc: "在表里。" },
      { id: "gone", label: "从未存在", col: 1, row: 2, dashed: true, desc: "回滚了。" },
    ],
    start: "in-tx",
    events: [
      { id: "commit", label: "提交事务" },
      { id: "rollback", label: "事务回滚" },
      { id: "send", label: "外送" },
    ],
    transitions: [
      { from: "in-tx", event: "commit", to: "pending", label: "提交" },
      { from: "in-tx", event: "rollback", to: "gone" },
    ],
  };

  it("点事件推进状态，底部记下走过的路径", () => {
    render(<StateMachine {...props} />);
    expect(caption()).toContain("还没提交。");
    fireEvent.click(screen.getByRole("button", { name: "提交事务" }));
    expect(caption()).toContain("在表里。");
    expect(stateOf("state-pending")).toBe("on");
    expect(screen.getByText("走过：事务中 → 待外送")).toBeTruthy();
  });

  it("点了不会发生的事件：说明变红，列出现在能发生的事件", () => {
    render(<StateMachine {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "外送" }));
    expect(caption()).toContain(
      "「外送」不会发生在「事务中」。现在能发生的是：「提交事务」、「事务回滚」。",
    );
    expect(screen.getByTestId("caption").getAttribute("data-tone")).toBe("bad");
  });

  it("终态没有事件可发生时提示从头来", () => {
    render(<StateMachine {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "事务回滚" }));
    fireEvent.click(screen.getByRole("button", { name: "提交事务" }));
    expect(caption()).toContain("没有了，点「从头来」");
    fireEvent.click(screen.getByRole("button", { name: "从头来" }));
    expect(screen.getByText("走过：事务中")).toBeTruthy();
  });
});

describe("Stepper", () => {
  const props = {
    title: "一条提醒怎么送到",
    nodes: [
      { id: "db", label: "PostgreSQL" },
      { id: "mq", label: "RabbitMQ" },
      { id: "notify", label: "notification" },
    ],
    links: [
      { from: "db", to: "mq" },
      { from: "mq", to: "notify" },
    ],
    steps: [
      { caption: "第一步。", focus: ["db"], tokens: [{ id: "m", label: "#7f3a", at: "db" }] },
      { caption: "第二步。", links: ["db>mq"], tokens: [{ id: "m", label: "#7f3a", at: "mq" }] },
      {
        caption: "第三步。",
        focus: ["notify"],
        tokens: [{ id: "m", label: "#7f3a", at: "notify" }],
        notes: [{ at: "notify", text: "E 的提醒 · 1 条", tone: "ok" as const }],
      },
    ],
    guesses: [
      {
        before: 3,
        question: "会收到几条？",
        options: ["1 条", "2 条"],
        answer: "A" as const,
        explain: "去重了。",
      },
    ],
  };

  it("下一步驱动；你猜之前停下，答完才能继续", () => {
    render(<Stepper {...props} />);
    expect(caption()).toBe("1第一步。");
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    expect(caption()).toBe("2第二步。");
    expect(stateOf("edge-db-mq")).toBe("on");
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    expect(screen.getByText("你猜会怎样？")).toBeTruthy();
    expect(caption()).toBe("2第二步。");
    const next = screen.getByRole("button", { name: "先选一个" });
    expect(next.hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: /2 条/ }));
    expect(screen.getByText("不是这样。")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "看看是不是" }));
    expect(caption()).toBe("3第三步。");
    expect(screen.getByText("E 的提醒 · 1 条")).toBeTruthy();
    expect(screen.getByRole("button", { name: "重新播放" })).toBeTruthy();
  });

  it("不猜直接看：揭晓答案；上一步关掉你猜", () => {
    render(<Stepper {...props} />);
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    fireEvent.click(screen.getByRole("button", { name: "上一步" }));
    expect(screen.queryByText("你猜会怎样？")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    fireEvent.click(screen.getByRole("button", { name: "不猜，直接看" }));
    expect(screen.getByText("去重了。")).toBeTruthy();
    expect(screen.getByTestId("token-m").textContent).toBe("#7f3a");
  });
});
