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
