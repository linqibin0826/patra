package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/**
 * 文献出版状态枚举。
 *
 * <p>字段映射：cat_publication.publication_status
 *
 * <p>状态说明：
 *
 * <ul>
 *   <li><b>PPUBLISH</b> - 纸质出版（Print Published）
 *   <li><b>EPUBLISH</b> - 电子出版（Electronic Published）
 *   <li><b>AHEADOFPRINT</b> - 预印版（Ahead of Print）
 *   <li><b>PUBMED</b> - 已收录到 PubMed
 *   <li><b>PUBMEDNOTMEDLINE</b> - 收录到 PubMed 但未收录到 MEDLINE
 *   <li><b>PREMEDLINE</b> - MEDLINE 预处理状态
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum PublicationStatus {

  /** 纸质出版 */
  PPUBLISH("ppublish", "Print Published"),

  /** 电子出版 */
  EPUBLISH("epublish", "Electronic Published"),

  /** 预印版 */
  AHEADOFPRINT("aheadofprint", "Ahead of Print"),

  /** 已收录到 PubMed */
  PUBMED("pubmed", "PubMed"),

  /** 收录到 PubMed 但未收录到 MEDLINE */
  PUBMEDNOTMEDLINE("pubmednotmedline", "PubMed Not MEDLINE"),

  /** MEDLINE 预处理状态 */
  PREMEDLINE("premedline", "Pre-MEDLINE");

  private final String code;
  private final String description;

  PublicationStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static PublicationStatus fromCode(String value) {
    Assert.notBlank(value, "出版状态代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (PublicationStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的出版状态：" + value);
  }
}
