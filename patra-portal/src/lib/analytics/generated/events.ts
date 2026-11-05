// 由 scripts/generate-analytics-types.mjs 从埋点契约生成，勿手改；改了 schema 跑 `pnpm gen:analytics`

export type PageType =
  | "home"
  | "search"
  | "article"
  | "journal"
  | "library"
  | "collection"
  | "profile"
  | "settings"
  | "notifications"
  | "post"
  | "history"
  | "privacy"
  | "auth"
  | "other";
export type ListType =
  | "feed_latest"
  | "feed_cited"
  | "feed_hot"
  | "search"
  | "related"
  | "related_topic"
  | "collection"
  | "library";
export interface ArticleViewed {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
}

export interface SearchPerformed {
  /**
   * 小写 UUID
   */
  search_id: string;
  mode: "keyword" | "exact_pmid" | "exact_doi";
  query: string;
  filters: {
    author?: string;
    year_from?: number;
    year_to?: number;
    /**
     * @maxItems 20
     */
    type?: string[];
    /**
     * @maxItems 20
     */
    evidence?: string[];
    /**
     * @maxItems 20
     */
    lang?: string[];
    /**
     * @maxItems 20
     *
     * Items: 雪花 / 自增 ID 的十进制字符串
     */
    venue?: string[];
    oa?: true;
  };
  sort: "latest" | "year";
  page: number;
  result_count: number;
}

export interface PdfDownloaded {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
}

export interface ExportCompleted {
  format: "ris" | "bibtex" | "csv" | "csl-json";
  scope: "items" | "collection" | "public_collection" | "library_all" | "library_unfiled";
  article_count: number;
}

export interface ArticleCollected {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
}

export type PageViewed = Record<string, never>;

export interface ArticleImpressed {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
  list_type:
    | "feed_latest"
    | "feed_cited"
    | "feed_hot"
    | "search"
    | "related"
    | "related_topic"
    | "collection"
    | "library";
  /**
   * 小写 UUID
   */
  list_id: string;
  position: number;
}

export interface ArticleClicked {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
  list_type:
    | "feed_latest"
    | "feed_cited"
    | "feed_hot"
    | "search"
    | "related"
    | "related_topic"
    | "collection"
    | "library";
  /**
   * 小写 UUID
   */
  list_id: string;
  position: number;
}

export interface FulltextRead {
  /**
   * catalog 文献 ID（门户 Paper.id）
   */
  article_id: string;
  visible_seconds: number;
}

export interface CitationCopied {
  /**
   * @minItems 1
   * @maxItems 100
   *
   * Items: catalog 文献 ID（门户 Paper.id）
   */
  article_ids: string[];
  style: "gb-t-7714-2015" | "vancouver" | "apa-7" | "ama-11";
}

/// 门户服务端发的 5 个事件：事件名 → 属性
export interface ServerEventProperties {
  article_viewed: ArticleViewed;
  search_performed: SearchPerformed;
  pdf_downloaded: PdfDownloaded;
  export_completed: ExportCompleted;
  article_collected: ArticleCollected;
}
/// 浏览器发的 5 个事件：事件名 → 属性
export interface ClientEventProperties {
  page_viewed: PageViewed;
  article_impressed: ArticleImpressed;
  article_clicked: ArticleClicked;
  fulltext_read: FulltextRead;
  citation_copied: CitationCopied;
}
/// 各事件当前的 schema 版本（`x-patra-version`）
export const SCHEMA_VERSIONS = {
  article_viewed: "1-0-0",
  search_performed: "1-0-0",
  pdf_downloaded: "1-0-0",
  export_completed: "1-0-0",
  article_collected: "1-0-0",
  page_viewed: "1-0-0",
  article_impressed: "1-0-0",
  article_clicked: "1-0-0",
  fulltext_read: "1-0-0",
  citation_copied: "1-0-0",
} as const;
