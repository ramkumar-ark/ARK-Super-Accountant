package com.arktech.superaccountant.statutory.repository;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatutorySectionRepository extends JpaRepository<StatutorySection, UUID> {

    List<StatutorySection> findByStatute(Statute statute);

    List<StatutorySection> findByStatuteOrderBySortOrderAscCodeAsc(Statute statute);

    Optional<StatutorySection> findByStatuteAndCode(Statute statute, String code);

    boolean existsByStatuteAndCode(Statute statute, String code);
}
