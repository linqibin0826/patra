package com.patra.catalog.api.dto;

import lombok.Builder;

/**
 * 期刊数据传输对象。
 *
 * <p>用于目录服务API的期刊信息传输,支持跨服务通信。
 *
 * @param title 期刊标题或缩写标题
 * @param issn 国际标准连续出版物号(ISSN)
 * @param issnType ISSN类型(印刷版、电子版或链接版)
 * @param publisher 出版商名称
 * @param country 期刊出版所在国家
 * @author linqibin
 * @since 0.1.0
 */
@Builder
public record JournalDTO(
    String title, String issn, String issnType, String publisher, String country) {}
