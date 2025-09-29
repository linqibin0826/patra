package com.patra.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumsTest {

    @Test
    void ingestDateType_fromCode_and_toCode() {
        assertThat(IngestDateType.fromCode("pdat")).isEqualTo(IngestDateType.PDAT);
        assertThat(IngestDateType.EDAT.toCode()).isEqualTo("EDAT");
        assertThatThrownBy(() -> IngestDateType.fromCode("xx")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provenanceCode_parse_alias_and_json() {
        assertThat(ProvenanceCode.parse("europe-pmc")).isEqualTo(ProvenanceCode.EPMC);
        assertThat(ProvenanceCode.parse(" MedLine ")).isEqualTo(ProvenanceCode.PUBMED);
        assertThat(ProvenanceCode.fromJson("openalex")).isEqualTo(ProvenanceCode.OPENALEX);
        assertThat(ProvenanceCode.PMC.toJson()).isEqualTo("PMC");
        assertThatThrownBy(() -> ProvenanceCode.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProvenanceCode.parse("unknownSource")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryConfigScope_fromCode_and_code() {
        assertThat(RegistryConfigScope.fromCode("source")).isEqualTo(RegistryConfigScope.SOURCE);
        assertThat(RegistryConfigScope.TASK.code()).isEqualTo("TASK");
        assertThatThrownBy(() -> RegistryConfigScope.fromCode("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistryConfigScope.fromCode("bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void priority_queue_value() {
        assertThat(Priority.LOWEST.queueValue()).isEqualTo(90);
        assertThat(Priority.HIGHEST.queueValue()).isEqualTo(10);
        assertThat(SortDirection.ASC.name()).isEqualTo("ASC");
    }
}

