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
};
