/// 候选主行里命中输入的部分加粗：不区分大小写找第一处，找不到就原样显示
export function HighlightedText({ text, query }: { text: string; query: string }) {
  const at = query === "" ? -1 : text.toLowerCase().indexOf(query.toLowerCase());
  if (at < 0) {
    return <>{text}</>;
  }
  return (
    <>
      {text.slice(0, at)}
      <b className="font-semibold">{text.slice(at, at + query.length)}</b>
      {text.slice(at + query.length)}
    </>
  );
}
