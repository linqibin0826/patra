---
id: BUG-2025-002
date: 2025-11-27
severity: high
status: fixed
tags: [xml, stax, dtd]
module: patra-catalog-infra
resolved_at: 2025-11-27
---

# XML 解析 DOCTYPE 声明错误

## 现象

解析 MeSH Qualifier XML 文件（`qual2025.xml`）时抛出异常：

```
javax.xml.stream.XMLStreamException: ParseError at [row,col]:[1,50]
Message: 在 publicId 和 systemId 之间需要有空格。
```

XML 文件包含 DOCTYPE 声明：
```xml
<?xml version="1.0"?>
<!DOCTYPE QualifierRecordSet SYSTEM "https://www.nlm.nih.gov/databases/dtd/nlmqualifierrecordset_20250101.dtd">
```

## 原因

`SUPPORT_DTD = false` 只禁用 DTD **验证**，不禁用 DOCTYPE **解析**。

Java StAX 解析器即使禁用了 DTD 支持，仍会尝试解析 DOCTYPE 声明的语法结构，遇到格式问题时报错。

## 解决方案

添加 `XMLResolver` 返回 `null`，让解析器忽略所有外部 DTD/实体引用：

```java
static {
  XML_INPUT_FACTORY = XMLInputFactory.newInstance();
  XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
  XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
  // 设置空的 XMLResolver，忽略所有外部 DTD/实体引用（避免 DOCTYPE 解析错误）
  XML_INPUT_FACTORY.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
}
```

这是一个**通用解决方案**，适用于所有包含 DOCTYPE 声明的 XML 文件。

## 相关
- 文件: `patra-catalog-infra/.../adapter/parser/XmlParserAdapter.java:65`
