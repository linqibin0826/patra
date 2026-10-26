/// 记在浏览器本地的选择（导出格式、引用格式，简报 §3.4、§3.6）：换设备不同步。隐私模式、禁用存储时读不到、
/// 写不进都不报错，读不到就用默认值。

export function readChoice<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
  try {
    const value = window.localStorage.getItem(key);
    return allowed.find((choice) => choice === value) ?? fallback;
  } catch {
    return fallback;
  }
}

export function writeChoice(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 写不进就算了，下次用默认值
  }
}
