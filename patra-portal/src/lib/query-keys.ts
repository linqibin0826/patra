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
};
