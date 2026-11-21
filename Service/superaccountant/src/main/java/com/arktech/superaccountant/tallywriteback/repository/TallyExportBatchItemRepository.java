package com.arktech.superaccountant.tallywriteback.repository;

import com.arktech.superaccountant.tallywriteback.models.TallyExportBatchItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TallyExportBatchItemRepository extends JpaRepository<TallyExportBatchItem, UUID> {

    @Query("SELECT i FROM TallyExportBatchItem i, TallyExportBatch b " +
           "WHERE i.batchId = b.id AND i.batchId = :batchId AND b.organizationId = :organizationId")
    List<TallyExportBatchItem> findByBatchIdAndOrganizationId(
            @Param("batchId") UUID batchId,
            @Param("organizationId") UUID organizationId);
}
