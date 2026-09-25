/** TanStack Query key 集中管理（见 .claude/rules/state.md）。 */
export const queryKeys = {
  /** 期刊候选：按去空白后的输入缓存 */
  venueSuggest: (q: string) => ["venue-suggest", q] as const,
};
