package com.arktech.superaccountant.gstr1.repository;

import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface PreparedReturnRowRepository extends JpaRepository<PreparedReturnRow, UUID> {

    List<PreparedReturnRow> findByPreparedReturnId(UUID preparedReturnId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PreparedReturnRow r WHERE r.preparedReturnId = :preparedReturnId")
    void deleteByPreparedReturnId(@Param("preparedReturnId") UUID preparedReturnId);

    @Query("""
            SELECT r FROM PreparedReturnRow r
            WHERE r.followUpId IN :followUpIds
              AND r.preparedReturnId IN (
                  SELECT p.id FROM PreparedReturn p
                  WHERE p.workflowId IN (
                      SELECT w.id FROM Workflow w
                      WHERE w.organizationId = :organizationId))
            """)
    List<PreparedReturnRow> findByOrganizationIdAndFollowUpIdIn(
            @Param("organizationId") UUID organizationId,
            @Param("followUpIds") java.util.Collection<UUID> followUpIds);
}
