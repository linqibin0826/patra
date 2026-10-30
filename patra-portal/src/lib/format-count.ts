/// 公开计数的写法（简报 v0.15 §0、§3.1）：千分位、不缩写；0 返回空串，调用方据此不写数字
export function formatCount(count: number): string {
  return count > 0 ? count.toLocaleString("en-US") : "";
}
