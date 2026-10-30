/// subject 子域的领域层。
///
/// 对象底座：每个被互动过的对象一行登记（外部对象锚定在外部标识上，本地对象由所属子域登记），互动数据只引用本地
/// `subject_id`；另外提供所有子域共用的用户名片、发表守卫与注销清理流程，以及评论与投稿经验共用的正文转纯文本。
///
/// 其他子域只能用它的公开包：`domain.subject.port.read`、`domain.subject.model.read`、`domain.subject.event`、
/// `domain.subject.port.gateway`、`domain.subject.port.user`、`domain.subject.port.markdown`、
/// `domain.subject.model.vo`、`domain.subject.model.enums`、`domain.subject.model.user`、
/// `domain.subject.exception`。
package dev.patra.interaction.domain.subject;
