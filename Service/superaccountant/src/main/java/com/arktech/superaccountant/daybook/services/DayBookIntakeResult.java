package com.arktech.superaccountant.daybook.services;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;

import java.util.List;

/**
 * Result of a day-book intake: the persisted job plus any non-fatal warnings.
 */
public record DayBookIntakeResult(DayBookUploadJob job, List<String> warnings) {
}
