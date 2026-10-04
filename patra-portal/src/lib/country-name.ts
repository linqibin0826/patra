const REGION_NAMES = new Intl.DisplayNames(["zh-CN"], { type: "region" });

/// ISO 3166-1 alpha-2 国家 / 地区码 → 简体中文名；非两位码或 ICU 不认识（原样返回代码）时为 null。
/// 只在服务端调用后把结果传给客户端组件，避免两端 ICU 数据版本不同导致 hydration 不一致。
export function countryName(code: string): string | null {
  if (!/^[a-z]{2}$/i.test(code)) {
    return null;
  }
  const upper = code.toUpperCase();
  try {
    const name = REGION_NAMES.of(upper);
    return name && name !== upper ? name : null;
  } catch {
    return null;
  }
}

/// 展示用标签「德国 · DE」；未知码原样返回。
export function countryLabel(code: string): string {
  const name = countryName(code);
  return name ? `${name} · ${code.toUpperCase()}` : code;
}
