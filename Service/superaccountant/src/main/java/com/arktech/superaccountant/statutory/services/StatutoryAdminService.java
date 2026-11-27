package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.login.security.services.UserDetailsImpl;
import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutoryAuditAction;
import com.arktech.superaccountant.statutory.models.StatutoryAuditEntry;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutoryRuleRequest;
import com.arktech.superaccountant.statutory.payload.request.CreateStatutorySectionRequest;
import com.arktech.superaccountant.statutory.repository.StatutoryAuditEntryRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.arktech.superaccountant.statutory.controllers.StatutoryAdminException;

import static com.arktech.superaccountant.statutory.services.StatutoryRuleValidator.B2CL_THRESHOLD;
import static com.arktech.superaccountant.statutory.services.StatutoryRuleValidator.GST_RATE;
import static com.arktech.superaccountant.statutory.services.StatutoryRuleValidator.IFF_CAP;

/**
 * App-wide statutory administration: list sections with their dated rule rows, add NEW
 * dated rows (append-only; the open predecessor is end-dated as supersession), create or
 * deactivate TDS sections, and audit every write. Never mutates effective history beyond
 * the supersession end date, so past-period analyses stay reproducible.
 */
@Service
public class StatutoryAdminService {

    /** Reference entities that block a section delete; each is a JPQL count by section code. */
    private static final Map<String, String> SECTION_REFERENCES = Map.of(
            "ledger mappings", "SELECT COUNT(m) FROM TdsLedgerMapping m WHERE m.section = :code",
            "preconfigured templates", "SELECT COUNT(m) FROM PreconfiguredMaster m WHERE m.tdsSection = :code",
            "upload snapshots", "SELECT COUNT(s) FROM UploadLedgerSnapshot s WHERE s.tdsSection = :code",
            "findings (section)", "SELECT COUNT(f) FROM TdsFinding f WHERE f.section = :code",
            "findings (proposed value)", "SELECT COUNT(f) FROM TdsFinding f WHERE f.proposedValue = :code",
            "deductee entries", "SELECT COUNT(d) FROM TdsDeducteeEntry d WHERE d.section = :code");

    @Autowired
    private StatutorySectionRepository sectionRepository;

    @Autowired
    private StatutorySectionRuleRepository ruleRepository;

    @Autowired
    private StatutoryAuditEntryRepository auditRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> view(UserDetailsImpl actor) {
        List<StatutorySection> sections = new ArrayList<>();
        for (Statute statute : Statute.values()) {
            sections.addAll(sectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(statute));
        }
        boolean canEdit = actor != null && actor.getAuthorities().stream()
                .anyMatch(a -> "ROLE_APP_ADMIN".equals(a.getAuthority()));
        List<Map<String, Object>> views = new ArrayList<>();
        for (StatutorySection section : sections) {
            views.add(sectionView(section, ruleRepository.findBySectionId(section.getId())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("canEdit", canEdit);
        body.put("sections", views);
        return body;
    }

    @Transactional
    public Map<String, Object> createSection(CreateStatutorySectionRequest req, UserDetailsImpl actor) {
        if (req.getCode() == null || !req.getCode().matches("^[0-9]{3}[A-Z]?(_[A-Z])?$")) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "code must match the TDS section pattern (e.g. 194C, 194I_A)");
        }
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "description is required");
        }
        if (req.getDescription().length() > 255) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "description must be at most 255 characters");
        }
        if (sectionRepository.existsByStatuteAndCode(Statute.INCOME_TAX, req.getCode())) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.CONFLICT,
                    "section " + req.getCode() + " already exists");
        }
        if (req.getRpu26qCode() != null && req.getRpu26qCode().length() > 10) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "rpu26qCode must be at most 10 characters");
        }
        if (req.getRpu140Code() != null && req.getRpu140Code().length() > 10) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "rpu140Code must be at most 10 characters");
        }
        StatutorySection section = new StatutorySection();
        section.setStatute(Statute.INCOME_TAX);
        section.setCode(req.getCode());
        section.setDescription(req.getDescription());
        section.setRpu26qCode(req.getRpu26qCode());
        section.setRpu140Code(req.getRpu140Code());
        section.setRpu140PayeeSplit(req.getRpu140PayeeSplit() != null && req.getRpu140PayeeSplit());
        section.setSortOrder(req.getSortOrder() != null ? req.getSortOrder()
                : sectionRepository.findByStatuteOrderBySortOrderAscCodeAsc(Statute.INCOME_TAX).stream()
                        .mapToInt(StatutorySection::getSortOrder).max().orElse(0) + 1);
        section.setActive(true);
        section.setSeeded(false);
        sectionRepository.save(section);
        audit(actor, StatutoryAuditAction.SECTION_CREATED, section, null, null);
        return sectionView(section, List.of());
    }

    @Transactional
    public Map<String, Object> setActive(UUID id, boolean active, UserDetailsImpl actor) {
        StatutorySection section = sectionRepository.findById(id).orElseThrow(() ->
                new StatutoryAdminException(org.springframework.http.HttpStatus.NOT_FOUND, "unknown section"));
        if (Statute.CGST.equals(section.getStatute())) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GST parameters must stay active; downstream reads require them");
        }
        if (section.getActive() == active) {
            return sectionView(section, ruleRepository.findBySectionId(section.getId()));
        }
        section.setActive(active);
        sectionRepository.save(section);
        audit(actor, active ? StatutoryAuditAction.SECTION_ACTIVATED
                : StatutoryAuditAction.SECTION_DEACTIVATED, section, null, null);
        return sectionView(section, ruleRepository.findBySectionId(section.getId()));
    }

    @Transactional
    public void deleteSection(UUID id, UserDetailsImpl actor) {
        StatutorySection section = sectionRepository.findById(id).orElseThrow(() ->
                new StatutoryAdminException(org.springframework.http.HttpStatus.NOT_FOUND, "unknown section"));
        if (section.getSeeded()) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.CONFLICT,
                    "seeded section " + section.getCode() + " cannot be deleted; deactivate instead");
        }
        StringBuilder refs = new StringBuilder();
        SECTION_REFERENCES.forEach((label, jpql) -> {
            long count = (long) entityManager.createQuery(jpql)
                    .setParameter("code", section.getCode())
                    .getSingleResult();
            if (count > 0) {
                if (!refs.isEmpty()) {
                    refs.append(", ");
                }
                refs.append(count).append(" ").append(label);
            }
        });
        if (!refs.isEmpty()) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.CONFLICT,
                    "referenced by " + refs + " — deactivate instead");
        }
        ruleRepository.deleteAll(ruleRepository.findBySectionId(section.getId()));
        sectionRepository.delete(section);
        audit(actor, StatutoryAuditAction.SECTION_DELETED, section, null, null);
    }

    @Transactional
    public Map<String, Object> addRule(UUID sectionId, CreateStatutoryRuleRequest req, UserDetailsImpl actor) {
        StatutorySection section = sectionRepository.findById(sectionId).orElseThrow(() ->
                new StatutoryAdminException(org.springframework.http.HttpStatus.NOT_FOUND, "unknown section"));
        List<StatutorySectionRule> existing = ruleRepository.findBySectionId(sectionId);
        StatutoryRuleValidator.Result result = StatutoryRuleValidator.validate(section, req, existing);
        if (!result.errors().isEmpty()) {
            throw new StatutoryAdminException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    String.join("; ", result.errors()));
        }
        if (Statute.CGST.equals(section.getStatute()) && StatutoryRuleValidator.GST_RATE.equals(section.getCode())
                && req.getRateNonCompany() == null) {
            req.setRateNonCompany(req.getRateCompany());
        }
        StatutorySectionRule rule = new StatutorySectionRule();
        rule.setSectionId(sectionId);
        rule.setRuleContext(req.getRuleContext() == null || req.getRuleContext().isBlank()
                ? "DEFAULT" : req.getRuleContext());
        rule.setRateCompany(req.getRateCompany());
        rule.setRateNonCompany(req.getRateNonCompany());
        rule.setThresholdPerTransaction(req.getThresholdPerTransaction());
        rule.setThresholdAnnual(req.getThresholdAnnual());
        rule.setThresholdMonthly(req.getThresholdMonthly());
        rule.setThresholdExcessOnly(req.getThresholdExcessOnly());
        rule.setHigherRateIfNoPan(req.getHigherRateIfNoPan());
        rule.setEffectiveFrom(req.getEffectiveFrom());
        rule.setEffectiveTo(req.getEffectiveTo());
        rule.setLegalReference(req.getLegalReference());
        rule.setSourceCitation(req.getSourceCitation());
        rule.setCreatedBy(actor.getUsername());
        rule.setUpdatedAt(Instant.now());
        rule = ruleRepository.save(rule);

        String details = "values=" + valueSummary(req);
        audit(actor, StatutoryAuditAction.RULE_ADDED, section, rule, details);
        if (result.superseded() != null) {
            result.superseded().setUpdatedAt(Instant.now());
            ruleRepository.save(result.superseded());
            audit(actor, StatutoryAuditAction.RULE_SUPERSEDED, section, result.superseded(),
                    "effectiveTo: null -> " + result.superseded().getEffectiveTo());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rule", ruleView(rule));
        body.put("superseded", result.superseded() != null ? ruleView(result.superseded()) : null);
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return auditRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, capped)).stream()
                .map(e -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", e.getId());
                    view.put("occurredAt", e.getOccurredAt());
                    view.put("actorUsername", e.getActorUsername());
                    view.put("action", e.getAction().name());
                    view.put("statute", e.getStatute().name());
                    view.put("sectionCode", e.getSectionCode());
                    view.put("ruleId", e.getRuleId());
                    view.put("details", e.getDetails());
                    return view;
                })
                .toList();
    }

    private Map<String, Object> sectionView(StatutorySection section, List<StatutorySectionRule> rules) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", section.getId());
        view.put("statute", section.getStatute().name());
        view.put("code", section.getCode());
        view.put("description", section.getDescription());
        view.put("rpu26qCode", section.getRpu26qCode());
        view.put("rpu140Code", section.getRpu140Code());
        view.put("rpu140PayeeSplit", section.getRpu140PayeeSplit());
        view.put("sortOrder", section.getSortOrder());
        view.put("active", section.getActive());
        view.put("seeded", section.getSeeded());
        view.put("rules", rules.stream()
                .sorted(Comparator.comparing(StatutorySectionRule::getRuleContext)
                        .thenComparing(StatutorySectionRule::getEffectiveFrom))
                .map(this::ruleView)
                .toList());
        return view;
    }

    private Map<String, Object> ruleView(StatutorySectionRule rule) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rule.getId());
        view.put("ruleContext", rule.getRuleContext());
        view.put("rateCompany", rule.getRateCompany());
        view.put("rateNonCompany", rule.getRateNonCompany());
        view.put("thresholdPerTransaction", rule.getThresholdPerTransaction());
        view.put("thresholdAnnual", rule.getThresholdAnnual());
        view.put("thresholdMonthly", rule.getThresholdMonthly());
        view.put("thresholdExcessOnly", rule.getThresholdExcessOnly());
        view.put("higherRateIfNoPan", rule.getHigherRateIfNoPan());
        view.put("effectiveFrom", rule.getEffectiveFrom());
        view.put("effectiveTo", rule.getEffectiveTo());
        view.put("legalReference", rule.getLegalReference());
        view.put("sourceCitation", rule.getSourceCitation());
        view.put("seeded", rule.isSeeded());
        view.put("createdBy", rule.getCreatedBy());
        view.put("updatedAt", rule.getUpdatedAt());
        return view;
    }

    private String valueSummary(CreateStatutoryRuleRequest req) {
        List<String> pairs = new ArrayList<>();
        pairs.add("from=" + req.getEffectiveFrom());
        pairs.add("to=" + req.getEffectiveTo());
        pair(pairs, "rateCompany", req.getRateCompany());
        pair(pairs, "rateNonCompany", req.getRateNonCompany());
        pair(pairs, "thresholdPerTransaction", req.getThresholdPerTransaction());
        pair(pairs, "thresholdAnnual", req.getThresholdAnnual());
        pair(pairs, "thresholdMonthly", req.getThresholdMonthly());
        pair(pairs, "higherRateIfNoPan", req.getHigherRateIfNoPan());
        return String.join("; ", pairs);
    }

    private void pair(List<String> pairs, String key, BigDecimal value) {
        if (value != null) {
            pairs.add(key + "=" + value);
        }
    }

    private void audit(UserDetailsImpl actor, StatutoryAuditAction action, StatutorySection section,
            StatutorySectionRule rule, String details) {
        StatutoryAuditEntry entry = new StatutoryAuditEntry();
        entry.setOccurredAt(Instant.now());
        entry.setActorUserId(actor.getId());
        entry.setActorUsername(actor.getUsername());
        entry.setAction(action);
        entry.setStatute(section.getStatute());
        entry.setSectionCode(section.getCode());
        entry.setSectionId(section.getId());
        entry.setRuleId(rule != null ? rule.getId() : null);
        entry.setDetails(details);
        auditRepository.save(entry);
    }
}
