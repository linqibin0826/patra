package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// 补充 MeSH 概念值对象。
///
/// 封装文献的补充 MeSH 概念（Supplementary Concept Record，SCR）信息。
///
/// **业务含义**：
///
/// MeSH SCR 是 MeSH 主题词表的补充，用于标注：
/// - **化学物质**：药物、化合物、蛋白质等
/// - **疾病变体**：罕见病、遗传变异等
/// - **实验方案**：研究协议、实验方法等
///
/// 与 MeshHeading（正式描述符）不同，SCR 更新更频繁，
/// 用于标注尚未进入正式词表的新概念。
///
/// **数据来源**：
///
/// 来自 PubMed XML 的 `<SupplMeshList>` 元素。
///
/// @param scrUi SCR UI（如 "C538003"）
/// @param supplOrder 顺序号
/// @author linqibin
/// @since 0.1.0
public record PublicationSupplMesh(String scrUi, Integer supplOrder) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证 SCR UI 不为空。
  public PublicationSupplMesh {
    Assert.isTrue(StrUtil.isNotBlank(scrUi), "SCR UI 不能为空");
  }

  /// 创建补充 MeSH 概念值对象。
  ///
  /// @param scrUi SCR UI
  /// @param supplOrder 顺序号
  /// @return 补充 MeSH 概念值对象
  public static PublicationSupplMesh of(String scrUi, Integer supplOrder) {
    return new PublicationSupplMesh(scrUi, supplOrder);
  }
}
