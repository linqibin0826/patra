/// 建议接口的输入归一，与 catalog 同一套（PAP-94）：NFKC、去首尾空白、折叠连续空白。浏览器判断「够不够
/// 2 个字」和门户转发前都用它，与后端的判定一致。
export function normalizeSuggestQuery(raw: string): string {
  return raw.normalize("NFKC").trim().replace(/\s+/g, " ");
}

/// 长度按码点计（catalog 要求 2–100 个码点）
export function suggestQueryLength(normalized: string): number {
  return [...normalized].length;
}
