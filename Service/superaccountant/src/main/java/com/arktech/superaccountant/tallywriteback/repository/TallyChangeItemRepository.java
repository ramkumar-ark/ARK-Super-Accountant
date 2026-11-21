package com.arktech.superaccountant.tallywriteback.repository;

import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TallyChangeItemRepository extends JpaRepository<TallyChangeItem, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TallyChangeItem i WHERE i.organizationId = :organizationId " +
           "AND i.ledgerKey = :ledgerKey AND i.field = :field " +
           "AND i.status IN (com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus.PENDING, " +
           "com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus.EXPORTED, " +
           "com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus.FAILED) " +
           "ORDER BY i.createdAt DESC")
    List<TallyChangeItem> findOpenForUpdate(
            @Param("organizationId") UUID organizationId,
            @Param("ledgerKey") String ledgerKey,
            @Param("field") TallyField field);

    List<TallyChangeItem> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<TallyChangeItem> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, TallyChangeStatus status);

    List<TallyChangeItem> findByOrganizationIdAndStatusInOrderByCreatedAtDesc(
            UUID organizationId, Collection<TallyChangeStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TallyChangeItem i WHERE i.organizationId = :organizationId " +
           "AND i.status IN :statuses ORDER BY i.createdAt DESC")
    List<TallyChangeItem> findForExport(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") Collection<TallyChangeStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TallyChangeItem i WHERE i.organizationId = :organizationId " +
           "AND i.id IN :ids AND i.status IN :statuses ORDER BY i.createdAt DESC")
    List<TallyChangeItem> findForExportByIds(
            @Param("organizationId") UUID organizationId,
            @Param("ids") Collection<UUID> ids,
            @Param("statuses") Collection<TallyChangeStatus> statuses);

    Optional<TallyChangeItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<TallyChangeItem> findByOrganizationIdAndLedgerKeyAndFieldAndStatusIn(
            UUID organizationId, String ledgerKey, TallyField field,
            Collection<TallyChangeStatus> statuses);
}
