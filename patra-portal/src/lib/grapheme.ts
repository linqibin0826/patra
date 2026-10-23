/// 按字素（用户眼里的「一个字」）切分：汉字、组合字符、emoji 组合序列各算一个，和后端计长度的口径一致
const SEGMENTER = new Intl.Segmenter("zh", { granularity: "grapheme" });

/// 字素列表
export function graphemes(value: string): string[] {
  return Array.from(SEGMENTER.segment(value), (part) => part.segment);
}

/// 字素数
export function graphemeCount(value: string): number {
  let count = 0;
  for (const _ of SEGMENTER.segment(value)) {
    count += 1;
  }
  return count;
}
