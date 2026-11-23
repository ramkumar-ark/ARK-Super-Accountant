package com.arktech.superaccountant.daybook.repository;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParsedVoucherRepository extends JpaRepository<ParsedVoucher, UUID> {

    List<ParsedVoucher> findByUploadJobId(UUID uploadJobId);

    /**
     * Turnover on specific ledgers for one date window: sum of (credit - debit) across
     * entries of non-cancelled vouchers dated in the window. Entry amounts preserve the
     * Tally sign (negative = credit), so negating the sum yields credit minus debit.
     * A voucher re-uploaded in several overlapping jobs counts once: only its copy from the
     * latest job (by GUID) is considered. Callers must pass ledger names normalized via
     * LedgerNames.normalize.
     */
    @Query("SELECT COALESCE(SUM(-e.amount), 0) FROM ParsedVoucherEntry e JOIN e.parsedVoucher v " +
           "WHERE e.organizationId = :organizationId AND v.isCancelled = :cancelled " +
           "AND v.voucherDate BETWEEN :from AND :to " +
           "AND (v.voucherGuid IS NULL OR NOT EXISTS (SELECT 1 FROM ParsedVoucher v2, " +
           "DayBookUploadJob j1, DayBookUploadJob j2 " +
           "WHERE v2.organizationId = v.organizationId AND v2.voucherGuid = v.voucherGuid " +
           "AND j1.id = v.uploadJobId AND j2.id = v2.uploadJobId " +
           "AND (j2.createdAt > j1.createdAt OR (j2.createdAt = j1.createdAt AND v2.id > v.id)))) " +
           "AND LOWER(TRIM(e.ledgerName)) IN :ledgerNames")
    java.math.BigDecimal sumCreditMinusDebitForLedgers(@Param("organizationId") UUID organizationId,
                                                       @Param("cancelled") boolean cancelled,
                                                       @Param("from") java.time.LocalDate from,
                                                       @Param("to") java.time.LocalDate to,
                                                       @Param("ledgerNames") List<String> ledgerNames);

    /**
     * Aggregate summary query returning voucher type breakdown for D-09.
     * Returns Object[] per type: [voucherTypeName, count, totalDebit, totalCredit, minDate, maxDate]
     *
     * NOTE: Callers (DayBookController) MUST verify job ownership (jobId belongs to orgId)
     * before calling this method to prevent cross-tenant data disclosure (T-4-03).
     */
    @Query("SELECT pv.voucherTypeName, COUNT(pv), " +
           "SUM(CASE WHEN pv.totalDebit IS NOT NULL THEN pv.totalDebit ELSE 0 END), " +
           "SUM(CASE WHEN pv.totalCredit IS NOT NULL THEN pv.totalCredit ELSE 0 END), " +
           "MIN(pv.voucherDate), MAX(pv.voucherDate) " +
           "FROM ParsedVoucher pv WHERE pv.uploadJobId = :jobId " +
           "GROUP BY pv.voucherTypeName ORDER BY pv.voucherTypeName")
    List<Object[]> findVoucherTypeSummary(@Param("jobId") UUID jobId);
}
