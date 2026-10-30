/// 按键去重，保留第一次出现的、顺序不变。「最有帮助」翻页期间数字会变，同一条可能出现在两页里（决策 D）
export function uniqueBy<T>(items: readonly T[], keyOf: (item: T) => string): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = keyOf(item);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}
