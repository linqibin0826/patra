/** TanStack Query key 集中管理（见 .claude/rules/state.md）。 */
export const queryKeys = {
  /** 期刊候选：全部输入的公共前缀（按前缀读取所有已缓存候选） */
  venueSuggestAll: ["venue-suggest"] as const,
  /** 期刊候选：按去空白后的输入缓存 */
  venueSuggest: (q: string) => ["venue-suggest", q] as const,
  /** 机构候选：按归一后的输入缓存 */
  organizationSuggest: (q: string) => ["organization-suggest", q] as const,
  /** MeSH 主题词候选：按归一后的输入缓存 */
  meshSuggest: (q: string) => ["mesh-suggest", q] as const,
  /** 当前用户：导航栏读取；注册、登录、登出之后由页面直接写入 */
  currentUser: ["current-user"] as const,
  /** 登录设备列表：安全页读取；改密码、踢设备后失效 */
  sessions: ["sessions"] as const,
  /** 我的状态：全部批次的公共前缀（收藏、移除后按前缀改所有屏里这篇的状态） */
  libraryStatusAll: ["library-status"] as const,
  /** 我的状态：一屏一批，按去重后的文献 ID 缓存 */
  libraryStatus: (keys: readonly string[]) => ["library-status", ...keys] as const,
  /** 文献库：概览与条目页的公共前缀（写操作之后整体失效） */
  library: ["library"] as const,
  /** 文献库概览：侧栏与收藏浮层共用 */
  libraryOverview: ["library", "overview"] as const,
  /** 文献库条目页：视图为 `all` / `unfiled` / 收藏夹 ID */
  libraryItems: (view: string, sort: string, page: number) =>
    ["library", "items", view, sort, page] as const,
  /** 复制引用：按样式与文献 ID 列表缓存（引用对话框与分享面板共用） */
  citations: (style: string, ids: readonly string[]) => ["citations", style, ids] as const,
  /** 导出 / 引用范围展开成的文献 ID：整库或整夹按范围参数缓存，勾选条目时为 null */
  scopeKeys: (scope: object | null) => ["scope-keys", scope] as const,
  /** 讨论区：全部讨论缓存的公共前缀。「这条是我发的」、联想里排除自己都按访客不同，身份变化时整体清掉 */
  discussion: ["discussion"] as const,
  /** 一个对象下的全部讨论缓存（列表的两种排序、单楼、联想）：写操作之后整体标为过期 */
  discussionSubject: (type: string, key: string) => ["discussion", type, key] as const,
  /** 全部「查看对话」与修改历史的缓存：写操作之后标为过期 */
  discussionConversationAll: ["discussion", "conversation"] as const,
  discussionRevisionsAll: ["discussion", "revisions"] as const,
  /** 讨论区一页页的楼（`useInfiniteQuery`）：按对象与根评论排序缓存 */
  discussionThreads: (type: string, key: string, sort: string) =>
    ["discussion", type, key, "threads", sort] as const,
  /** 单楼视图：目标评论所在的整楼；目标不存在时数据是 `{ missing: true }` */
  discussionSingle: (type: string, key: string, commentId: string) =>
    ["discussion", type, key, "single", commentId] as const,
  /** 「查看对话」的回复链 */
  discussionConversation: (commentId: string) => ["discussion", "conversation", commentId] as const,
  /** 一条评论的修改历史 */
  discussionRevisions: (commentId: string) => ["discussion", "revisions", commentId] as const,
  /** @ 联想：按对象与查询词缓存 */
  discussionMentions: (type: string, key: string, q: string) =>
    ["discussion", type, key, "mentions", q] as const,
  /** 提醒：全部提醒缓存的公共前缀（未读数与两个标签的列表），身份变化时整体清掉 */
  notifications: ["notifications"] as const,
  /** 未读数：数据是 `{ counts, etag }`，ETag 留给下一次轮询带 `If-None-Match` */
  notificationUnread: ["notifications", "unread"] as const,
  /** 两个标签的提醒列表：已读之后一起改 */
  notificationListAll: ["notifications", "list"] as const,
  /** 提醒列表（`useInfiniteQuery`）：按标签缓存 */
  notificationList: (tab: string) => ["notifications", "list", tab] as const,
  /** 投稿经验：全部缓存的公共前缀。`mine`、本人的匿名经验按访客不同，身份变化时整体清掉 */
  experience: ["experience"] as const,
  /** 一本期刊的经验缓存（两个窗口的汇总与列表）：发布、编辑、删除之后整体处理 */
  experienceJournal: (journalId: string) => ["experience", "journal", journalId] as const,
  /** 期刊页「同行经验」汇总：按窗口（`3y` / `all`）缓存 */
  experienceSummary: (journalId: string, window: string) =>
    ["experience", "journal", journalId, "summary", window] as const,
  /** 期刊页经验列表两种排序的公共前缀：发布之后一起重置 */
  experienceListAll: (journalId: string) => ["experience", "journal", journalId, "list"] as const,
  /** 期刊页经验列表（`useInfiniteQuery`）：按排序（`latest` / `helpful`）缓存 */
  experienceList: (journalId: string, sort: string) =>
    ["experience", "journal", journalId, "list", sort] as const,
  /** 主页「投稿经验」标签（`useInfiniteQuery`） */
  experienceUser: (userId: string) => ["experience", "user", userId] as const,
  /** 一条经验的修改历史 */
  experienceRevisions: (postId: string) => ["experience", "revisions", postId] as const,
};
