/// 讨论区在不同对象下的文案（简报 §3.1、§3.5、§3.11、§3.21）：文献页与经验页的回复区只差这些
export interface DiscussionCopy {
  /// 区块标题（`SectionEyebrow`，大写显示）
  title: string;
  emptyTitle: string;
  emptyBody: string;
  /// 空状态的「可以讨论什么」，经验页没有
  emptyGuide: readonly { label: string; text: string }[];
  /// 列表不为空时顶部编辑器收起态的占位
  composerPlaceholder: string;
  /// 空状态里展开的编辑器的占位
  emptyPlaceholder: string;
  unavailableTitle: string;
  unavailableBody: string;
}

export const PAPER_DISCUSSION_COPY: DiscussionCopy = {
  title: "讨论 · Discussion",
  emptyTitle: "这篇文献还没有讨论",
  emptyBody:
    "对方法、数据或结论有疑问，或者知道后续研究的结果？\n写下来，之后读到这篇文献的人都能看到。",
  emptyGuide: [
    { label: "方法", text: "样本量、对照、统计方法是否站得住" },
    { label: "证据", text: "后续研究有没有重复出结果：直接写 PMID 或 DOI，会自动变成链接" },
    { label: "应用", text: "对临床或科研实践意味着什么" },
  ],
  composerPlaceholder: "写下你对这篇文献的看法、疑问或补充证据…",
  emptyPlaceholder: "例如：每组只有 47 例，而且是回顾性前后对照……",
  unavailableTitle: "讨论暂不可用",
  unavailableBody: "文献内容不受影响，请稍后再试。",
};

export const POST_REPLIES_COPY: DiscussionCopy = {
  title: "回复 · Replies",
  emptyTitle: "还没有人回复这条投稿经验",
  emptyBody: "对审稿过程有疑问，可以在这里追问作者。",
  emptyGuide: [],
  composerPlaceholder: "对这条投稿经验有疑问？在这里追问…",
  emptyPlaceholder: "对这条投稿经验有疑问？在这里追问…",
  unavailableTitle: "回复暂不可用",
  unavailableBody: "请稍后再试。",
};

/// 单楼视图顶部回复框的占位（简报 §3.10）
export const SINGLE_REPLY_PLACEHOLDER = "回复这一楼…";
