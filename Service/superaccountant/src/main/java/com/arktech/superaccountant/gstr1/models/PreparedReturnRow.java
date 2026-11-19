package com.arktech.superaccountant.gstr1.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "prepared_return_rows")
@Data
@NoArgsConstructor
public class PreparedReturnRow {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "prepared_return_id", nullable = false)
    private UUID preparedReturnId;

    @Column(name = "table_code", nullable = false, length = 16)
    private String tableCode;

    @Column(name = "row_key", nullable = false, length = 128)
    private String rowKey;

    @Column(name = "source_voucher_key", length = 128)
    private String sourceVoucherKey;

    // TEXT without @Lob: PostgreSQL OID lobs are not readable outside their transaction
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "is_amendment", nullable = false)
    private boolean isAmendment;

    @Column(name = "original_period", length = 16)
    private String originalPeriod;

    @Column(name = "original_doc_key", length = 128)
    private String originalDocKey;

    @SuppressWarnings("unchecked")
    public Map<String, Object> payload() {
        try {
            return MAPPER.readValue(payload, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid row payload for " + rowKey, e);
        }
    }
}
