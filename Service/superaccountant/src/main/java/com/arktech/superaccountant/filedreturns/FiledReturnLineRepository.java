package com.arktech.superaccountant.filedreturns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiledReturnLineRepository extends JpaRepository<FiledReturnLine, UUID> {
    List<FiledReturnLine> findBySnapshotIdOrderByLineNo(UUID snapshotId);

    /** Sum of taxable values on turnover rows (3.1a/3.1b/3.1c/3.1e) across snapshots. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(l.taxable), 0) FROM FiledReturnLine l
            WHERE l.snapshotId IN :snapshotIds AND LOWER(l.tableCode) IN :tableCodes
            """)
    java.math.BigDecimal sumTaxableForTurnover(
            @org.springframework.data.repository.query.Param("snapshotIds") java.util.List<UUID> snapshotIds,
            @org.springframework.data.repository.query.Param("tableCodes") java.util.List<String> tableCodes);
}
