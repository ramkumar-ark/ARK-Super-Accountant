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
}
