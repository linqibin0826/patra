/// 改地址的查询参数（保留路径、其余参数与片段），不触发服务端重新渲染：Next 把原生 history 同步进 `useSearchParams`
export function writeQuery(
  change: (query: URLSearchParams) => void,
  how: "push" | "replace",
): void {
  const query = new URLSearchParams(window.location.search);
  change(query);
  const search = query.toString();
  const url = `${window.location.pathname}${search ? `?${search}` : ""}${window.location.hash}`;
  if (how === "push") {
    window.history.pushState(null, "", url);
  } else {
    window.history.replaceState(null, "", url);
  }
}
