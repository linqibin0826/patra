/// 给 `http.Server` 的 `request` 事件套一层：先删掉入站的 `x-forwarded-for`，再交给原来的监听器。重复调用只包一层。
export function install(): void;
