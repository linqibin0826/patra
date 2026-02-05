package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;

/// 人物主题值对象。
///
/// 封装文献描述的人物主题信息（传记类、历史类文献的描述对象）。
///
/// **业务含义**：
///
/// 人物主题（PersonalNameSubject）是文献内容描述的对象，而非作者或参与者。
/// 常见于：
/// - **传记类文献**：描述某人生平事迹
/// - **历史类文献**：研究历史人物的贡献
/// - **纪念类文献**：追思已故科学家
///
/// **与 Author/Investigator 的区别**：
///
/// - Author：文章署名作者
/// - Investigator：研究参与者
/// - PersonalNameSubject：文章描述的主题人物
///
/// **设计说明**：
///
/// 人物主题不需要去重：
/// - 同一历史人物可能有多种名字拼写形式
/// - 不同文献引用同一人物时可能使用不同的描述
/// - 人物主题记录与文献绑定，不作为独立实体管理
///
/// @param lastName 姓
/// @param foreName 名
/// @param initials 姓名缩写
/// @param suffix 后缀/头衔
/// @param dates 生卒年代（如 "1820-1910"）
/// @param description 人物描述
/// @param subjectType 主题类型（如 "biography", "history"）
/// @param identifier 人物标识符（如 VIAF ID）
/// @param orderNum 顺序号
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationPersonalNameSubject(
    String lastName,
    String foreName,
    String initials,
    String suffix,
    String dates,
    String description,
    String subjectType,
    String identifier,
    Integer orderNum)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证姓氏不为空。
  public PublicationPersonalNameSubject {
    Assert.isTrue(StrUtil.isNotBlank(lastName), "人物主题姓氏不能为空");
  }

  /// 判断是否有生卒年代。
  ///
  /// @return true 如果有 dates
  public boolean hasDates() {
    return StrUtil.isNotBlank(dates);
  }

  /// 判断是否有人物描述。
  ///
  /// @return true 如果有 description
  public boolean hasDescription() {
    return StrUtil.isNotBlank(description);
  }

  /// 判断是否有主题类型。
  ///
  /// @return true 如果有 subjectType
  public boolean hasSubjectType() {
    return StrUtil.isNotBlank(subjectType);
  }

  /// 判断是否有标识符。
  ///
  /// @return true 如果有 identifier
  public boolean hasIdentifier() {
    return StrUtil.isNotBlank(identifier);
  }

  /// 获取显示名称。
  ///
  /// @return 格式化的显示名称
  public String getDisplayName() {
    StringBuilder sb = new StringBuilder();
    sb.append(lastName);
    if (StrUtil.isNotBlank(foreName)) {
      sb.append(", ").append(foreName);
    } else if (StrUtil.isNotBlank(initials)) {
      sb.append(" ").append(initials);
    }
    if (StrUtil.isNotBlank(dates)) {
      sb.append(" (").append(dates).append(")");
    }
    return sb.toString();
  }
}
