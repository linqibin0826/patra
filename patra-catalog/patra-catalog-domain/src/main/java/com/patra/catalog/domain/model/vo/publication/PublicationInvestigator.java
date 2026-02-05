package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;

/// 文献研究者值对象。
///
/// 封装与文献关联的研究者信息（非作者身份的研究参与者）。
///
/// **业务含义**：
///
/// 研究者（Investigator）指参与研究但未列为文章作者的人员，常见于：
/// - **大型临床试验**：协调中心人员、数据监测委员会成员
/// - **多中心研究**：各研究站点的负责人
/// - **合作项目**：资助机构指派的项目官员
///
/// **与 Author 的区别**：
///
/// - Author：文章署名作者，对内容负责
/// - Investigator：研究参与者，不在署名列表中
///
/// **去重策略**：
///
/// 使用 `dedupKey` 进行研究者去重：
/// - 计算规则：`MD5(LOWER(lastName) + "|" + LOWER(foreName) + "|" + LOWER(COALESCE(orcid, "")))`
/// - 优先使用 ORCID 匹配，其次使用 dedupKey 匹配
///
/// @param lastName 姓
/// @param foreName 名
/// @param initials 姓名缩写
/// @param suffix 后缀（如 "Jr.", "MD"）
/// @param orcid ORCID 标识符（可能为 null）
/// @param affiliationName 机构名称
/// @param dedupKey 去重键（MD5 哈希）
/// @param orderNum 顺序号
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationInvestigator(
    String lastName,
    String foreName,
    String initials,
    String suffix,
    String orcid,
    String affiliationName,
    String dedupKey,
    Integer orderNum)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证姓氏和去重键不为空。
  public PublicationInvestigator {
    Assert.isTrue(StrUtil.isNotBlank(lastName), "研究者姓氏不能为空");
    Assert.isTrue(StrUtil.isNotBlank(dedupKey), "去重键不能为空");
  }

  /// 判断是否有 ORCID。
  ///
  /// @return true 如果有 ORCID
  public boolean hasOrcid() {
    return StrUtil.isNotBlank(orcid);
  }

  /// 判断是否有机构信息。
  ///
  /// @return true 如果有机构名称
  public boolean hasAffiliation() {
    return StrUtil.isNotBlank(affiliationName);
  }

  /// 判断是否有名字。
  ///
  /// @return true 如果有 foreName
  public boolean hasForeName() {
    return StrUtil.isNotBlank(foreName);
  }

  /// 判断是否有姓名缩写。
  ///
  /// @return true 如果有 initials
  public boolean hasInitials() {
    return StrUtil.isNotBlank(initials);
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
    if (StrUtil.isNotBlank(suffix)) {
      sb.append(" ").append(suffix);
    }
    return sb.toString();
  }
}
