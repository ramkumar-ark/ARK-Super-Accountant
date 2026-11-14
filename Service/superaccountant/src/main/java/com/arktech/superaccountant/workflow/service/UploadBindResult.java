package com.arktech.superaccountant.workflow.service;

import com.arktech.superaccountant.workflow.models.Workflow;

import java.util.List;

/**
 * Result of binding an uploaded day-book to a workflow: the updated workflow plus
 * the intake warnings that the caller should surface to the user.
 */
public record UploadBindResult(Workflow workflow, List<String> warnings) {
}
