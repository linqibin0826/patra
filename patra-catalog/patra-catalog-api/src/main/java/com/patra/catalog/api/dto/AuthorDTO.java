package com.patra.catalog.api.dto;

import java.util.List;
import lombok.Builder;

/**
 * 作者数据传输对象。
 *
 * <p>用于目录服务API的作者信息传输,支持跨服务通信。
 *
 * @param lastName 作者的姓氏或家族名
 * @param foreName 作者的名字或给定名
 * @param initials 作者姓名的首字母缩写
 * @param affiliations 作者的机构隶属列表
 * @param identifier 作者的唯一标识符(例如 ORCID)
 * @param identifierSource 作者标识符的来源系统
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record AuthorDTO(
    String lastName,
    String foreName,
    String initials,
    List<String> affiliations,
    String identifier,
    String identifierSource) {}
