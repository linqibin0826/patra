/** TanStack Query key 集中管理（见 .claude/rules/state.md）。 */
export const queryKeys = {
  /** 期刊候选：全部输入的公共前缀（按前缀读取所有已缓存候选） */
  venueSuggestAll: ["venue-suggest"] as const,
  /** 期刊候选：按去空白后的输入缓存 */
  venueSuggest: (q: string) => ["venue-suggest", q] as const,
};
