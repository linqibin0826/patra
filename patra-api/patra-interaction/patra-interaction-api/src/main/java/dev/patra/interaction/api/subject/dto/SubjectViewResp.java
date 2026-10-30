package dev.patra.interaction.api.subject.dto;

import lombok.Builder;

/// 一个登记对象的展示信息。快照各字段在不同类型里的含义同 interaction 的 `SubjectSnapshot`：
///
/// | 类型 | `title` | `journal` | `publishedDate` | `abbreviation` |
/// |---|---|---|---|---|
/// | `PUBLICATION` | 标题 | 期刊全称 | 出版日期 | 空 |
/// | `JOURNAL` | 期刊全称 | 空 | 空 | MEDLINE 刊名缩写 |
/// | `POST` | 空 | 所在期刊全称 | 投稿月份 `yyyy-MM` | 所在期刊缩写 |
///
/// @param subjectId 登记表 ID
/// @param type 对象种类：`PUBLICATION` / `JOURNAL` / `POST`（以后可能加值，调用方不认识的应跳过）
/// @param key 门户链接里的 ID（文献、期刊是来源内部 ID，经验是帖子 ID）；来源里整行消失时为空
/// @param available 对象是否还在（文献下线、经验删除后为假）
/// @param title 标题，可空
/// @param journal 期刊全称，可空
/// @param publishedDate 出版日期或投稿月份（ISO 8601 降精度），可空
/// @param abbreviation 期刊缩写，可空
/// @param ownerId 本地对象（经验）的所有者；外部对象为空
/// @param ownerAnonymous 经验是否匿名发布；外部对象为假
@Builder
public record SubjectViewResp(
    Long subjectId,
    String type,
    String key,
    boolean available,
    String title,
    String journal,
    String publishedDate,
    String abbreviation,
    Long ownerId,
    boolean ownerAnonymous) {}
