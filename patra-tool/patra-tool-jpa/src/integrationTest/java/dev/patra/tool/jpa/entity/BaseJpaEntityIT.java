package dev.patra.tool.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.jpa.support.JpaITAuditedNoteDao;
import dev.patra.tool.jpa.support.JpaITAuditedNoteEntity;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/// 主表基类 `BaseJpaEntity` 的映射：备注只读、IP 存成 `INET`。
///
/// 备注给数据处理和人工核对用，只在库里直接写。仓储常用「由聚合重建实体再合并保存」的写法，重建出的实体不带备注；
/// 备注列若可写，每次保存都会把库里的备注清空。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("主表基类的映射")
class BaseJpaEntityIT {

  @Autowired private JpaITAuditedNoteDao noteDao;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("合并保存一个不带备注的实体，库里的备注不被覆盖")
  void should_keep_record_remarks_when_entity_merged() {
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("first");
    JpaITAuditedNoteEntity saved = noteDao.saveAndFlush(note);
    jdbc.update(
        "UPDATE jpa_it_audited_note SET record_remarks = '{\"note\":\"人工核对\"}' WHERE id = ?",
        note.getId());

    JpaITAuditedNoteEntity rebuilt = new JpaITAuditedNoteEntity();
    rebuilt.setId(note.getId());
    rebuilt.setVersion(saved.getVersion());
    rebuilt.setContent("second");
    noteDao.saveAndFlush(rebuilt);

    assertThat(
            jdbc.queryForObject(
                "SELECT record_remarks ->> 'note' FROM jpa_it_audited_note WHERE id = ?",
                String.class,
                note.getId()))
        .isEqualTo("人工核对");
    assertThat(
            jdbc.queryForObject(
                "SELECT content FROM jpa_it_audited_note WHERE id = ?", String.class, note.getId()))
        .isEqualTo("second");
  }

  @Test
  @DisplayName("IP 存成 INET，读回相等")
  void should_round_trip_ip_address_as_inet() throws UnknownHostException {
    InetAddress address = InetAddress.getByName("2001:db8::1");
    JpaITAuditedNoteEntity note = new JpaITAuditedNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setIpAddress(address);

    noteDao.saveAndFlush(note);

    assertThat(
            jdbc.queryForObject(
                "SELECT host(ip_address) FROM jpa_it_audited_note WHERE id = ?",
                String.class,
                note.getId()))
        .isEqualTo("2001:db8::1");
    assertThat(noteDao.findById(note.getId()).orElseThrow().getIpAddress()).isEqualTo(address);
  }
}
