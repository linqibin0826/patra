/// subject 子域的基础设施层。
///
/// 对象底座：每个被互动过的外部对象一行登记，互动数据只引用本地 `subject_id`，对象身份锚定在外部标识上。
///
/// 其他子域只能用它的公开包：`domain.subject.port.read`、`domain.subject.model.read`、
/// `domain.subject.port.gateway`、`domain.subject.model.vo`、`domain.subject.model.enums`。
package dev.patra.interaction.infra.subject;
