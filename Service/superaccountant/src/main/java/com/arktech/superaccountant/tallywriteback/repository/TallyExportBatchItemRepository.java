package com.arktech.superaccountant.tallywriteback.repository;

import com.arktech.superaccountant.tallywriteback.models.TallyExportBatchItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TallyExportBatchItemRepository extends JpaRepository<TallyExportBatchItem, UUID> {

    List<TallyExportBatchItem> findByBatchId(UUID batchId);
}
