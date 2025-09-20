package com.patra.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文献数据常见日期类型（用于区分不同日期字段，如 PubMed 中的 PDAT/EDAT/MHDA）。
 *
 * <p>示例含义：
 * <ul>
 *   <li><b>PDAT</b>（Publication Date）：论文正式发表日期，常用于检索过滤。</li>
 *   <li><b>EDAT</b>（Entrez Date）：论文被收录至数据库的日期，反映抓取/入库时间。</li>
 *   <li><b>MHDA</b>（MeSH Date）：论文被分配 MeSH 主题词的日期，表示主题标注完成。</li>
 * </ul>
 *
 * <p>引用：PubMed 帮助文档等。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@RequiredArgsConstructor
public enum IngestDateType {

    PDAT("PDAT", "Publication Date", "The official publication date of the article"),
    EDAT("EDAT", "Entrez Date", "The date when the article was entered into PubMed"),
    MHDA("MHDA", "MeSH Date", "The date when MeSH indexing was assigned to the article");

    /** PubMed 中用于标识该日期类型的代码（如 "PDAT"）。 */
    private final String code;

    /** 日期类型短名称（例如 “Publication Date”）。 */
    private final String name;

    /** 该日期类型的详细说明。 */
    private final String description;

    /**
     * 工厂方法：由字符串 code 创建枚举（@JsonCreator）。
     */
    @JsonCreator
    public static IngestDateType fromCode(String code) {
        for (IngestDateType type : IngestDateType.values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    /**
     * JSON 序列化输出 code（@JsonValue）。
     */
    @JsonValue
    public String toCode() {
        return this.code;
    }

}
