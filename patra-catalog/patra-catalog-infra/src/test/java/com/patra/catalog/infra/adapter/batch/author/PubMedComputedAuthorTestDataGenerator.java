package com.patra.catalog.infra.adapter.batch.author;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/// PubMed Computed Authors 测试数据生成器。
///
/// 生成符合 PubMed Computed Authors JSON Lines 格式的测试数据。
///
/// **数据格式**：
///
/// ```json
/// {"name": "MAKAR+A", "names": ["Makar,Artur,A"], "orcid": ["0000-0001-7121-5322"], "pmids":
// [32708434]}
/// ```
///
/// **生成规则**：
///
/// - 约 30% 的作者有 ORCID
/// - 每个作者有 1-3 个名字变体
/// - 部分作者包含中文姓名变体
///
/// @author linqibin
/// @since 0.1.0
public class PubMedComputedAuthorTestDataGenerator {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
  private static final Random RANDOM = new Random(42); // 固定种子确保可重复

  // 常见姓氏
  private static final String[] LAST_NAMES = {
    "Smith",
    "Johnson",
    "Williams",
    "Brown",
    "Jones",
    "Garcia",
    "Miller",
    "Davis",
    "Rodriguez",
    "Martinez",
    "Hernandez",
    "Lopez",
    "Gonzalez",
    "Wilson",
    "Anderson",
    "Thomas",
    "Taylor",
    "Moore",
    "Jackson",
    "Martin",
    "Lee",
    "Perez",
    "Thompson",
    "White",
    "Harris",
    "Sanchez",
    "Clark",
    "Ramirez",
    "Lewis",
    "Robinson",
    "Walker",
    "Young",
    "Allen",
    "King",
    "Wright",
    "Scott",
    "Torres",
    "Nguyen",
    "Hill",
    "Flores",
    "Green",
    "Adams",
    "Nelson",
    "Baker",
    "Hall",
    "Rivera",
    "Campbell",
    "Mitchell",
    "Carter"
  };

  // 常见名字
  private static final String[] FIRST_NAMES = {
    "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
    "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
    "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
    "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
    "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle"
  };

  // 中文姓氏
  private static final String[] CHINESE_LAST_NAMES = {
    "Wang", "Li", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Wu", "Zhou",
    "Xu", "Sun", "Ma", "Zhu", "Hu", "Guo", "Lin", "He", "Gao", "Luo"
  };

  // 中文姓氏对应的汉字
  private static final String[] CHINESE_LAST_NAMES_CN = {
    "王", "李", "张", "刘", "陈", "杨", "黄", "赵", "吴", "周",
    "徐", "孙", "马", "朱", "胡", "郭", "林", "何", "高", "罗"
  };

  // 中文名字
  private static final String[] CHINESE_FIRST_NAMES = {
    "Wei", "Fang", "Lei", "Yang", "Jie", "Xia", "Min", "Juan", "Yan", "Hong"
  };

  // 中文名字对应的汉字
  private static final String[] CHINESE_FIRST_NAMES_CN = {
    "伟", "芳", "磊", "洋", "杰", "霞", "敏", "娟", "燕", "红"
  };

  /// 生成指定数量的测试数据并写入输出流。
  ///
  /// @param count 记录数量
  /// @param outputStream 输出流
  /// @throws IOException 写入失败时
  public static void generate(int count, OutputStream outputStream) throws IOException {
    try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
      for (int i = 0; i < count; i++) {
        ObjectNode record = generateRecord(i);
        writer.write(OBJECT_MAPPER.writeValueAsString(record));
        writer.write("\n");
      }
    }
  }

  /// 生成单条测试记录。
  ///
  /// @param index 记录索引
  /// @return JSON 对象
  private static ObjectNode generateRecord(int index) {
    ObjectNode record = OBJECT_MAPPER.createObjectNode();

    // 决定是否生成中文姓名（约 20%）
    boolean isChinese = RANDOM.nextInt(100) < 20;

    String lastName;
    String firstName;
    String initials;
    List<String> nameVariants = new ArrayList<>();

    if (isChinese) {
      // 生成中文作者
      int lastNameIdx = RANDOM.nextInt(CHINESE_LAST_NAMES.length);
      int firstNameIdx = RANDOM.nextInt(CHINESE_FIRST_NAMES.length);

      lastName = CHINESE_LAST_NAMES[lastNameIdx];
      firstName = CHINESE_FIRST_NAMES[firstNameIdx];
      initials = firstName.substring(0, 1).toUpperCase();

      // 英文变体
      nameVariants.add(lastName + "," + firstName + "," + initials);

      // 中文变体（约 50% 的中文作者有中文变体）
      if (RANDOM.nextBoolean()) {
        nameVariants.add(
            CHINESE_LAST_NAMES_CN[lastNameIdx] + "," + CHINESE_FIRST_NAMES_CN[firstNameIdx]);
      }
    } else {
      // 生成英文作者
      lastName = LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)];
      firstName = FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)];
      initials = firstName.substring(0, 1).toUpperCase();

      // 添加可能的中间名缩写
      if (RANDOM.nextBoolean()) {
        initials += (char) ('A' + RANDOM.nextInt(26));
      }

      // 完整名字变体
      nameVariants.add(lastName + "," + firstName + "," + initials);

      // 额外变体（约 30%）
      if (RANDOM.nextInt(100) < 30) {
        nameVariants.add(lastName + "," + initials);
      }
    }

    // 生成 normalizedKey：姓氏大写 + "+" + 缩写大写
    String normalizedKey = lastName.toUpperCase() + "+" + initials;
    // 添加索引确保唯一性
    normalizedKey = normalizedKey + "_" + index;

    record.put("name", normalizedKey);

    // 名字变体数组
    ArrayNode namesArray = record.putArray("names");
    for (String variant : nameVariants) {
      namesArray.add(variant);
    }

    // ORCID（约 30% 的作者有）
    if (RANDOM.nextInt(100) < 30) {
      ArrayNode orcidArray = record.putArray("orcid");
      orcidArray.add(generateOrcid(index));
    }

    // PMIDs（可选，约 80% 有）
    if (RANDOM.nextInt(100) < 80) {
      ArrayNode pmidsArray = record.putArray("pmids");
      int pmidCount = 1 + RANDOM.nextInt(5);
      for (int i = 0; i < pmidCount; i++) {
        pmidsArray.add(30000000 + RANDOM.nextInt(10000000));
      }
    }

    return record;
  }

  /// 生成有效的 ORCID（符合格式和校验位）。
  ///
  /// @param seed 种子值
  /// @return 有效的 ORCID
  private static String generateOrcid(int seed) {
    // 生成前 15 位数字
    StringBuilder sb = new StringBuilder();
    Random r = new Random(seed);
    for (int i = 0; i < 15; i++) {
      sb.append(r.nextInt(10));
    }

    // 计算校验位（ISO 7064 Mod 11-2）
    String digits = sb.toString();
    int total = 0;
    for (int i = 0; i < 15; i++) {
      int digit = Character.getNumericValue(digits.charAt(i));
      total = (total + digit) * 2;
    }
    int remainder = total % 11;
    int checkDigit = (12 - remainder) % 11;
    char check = (checkDigit == 10) ? 'X' : (char) ('0' + checkDigit);

    sb.append(check);

    // 格式化为 XXXX-XXXX-XXXX-XXXX
    String raw = sb.toString();
    return raw.substring(0, 4)
        + "-"
        + raw.substring(4, 8)
        + "-"
        + raw.substring(8, 12)
        + "-"
        + raw.substring(12, 16);
  }

  /// 测试入口（用于生成测试数据文件）。
  public static void main(String[] args) throws IOException {
    generate(500, System.out);
  }
}
