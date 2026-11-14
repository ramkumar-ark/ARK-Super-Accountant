package com.arktech.superaccountant.gst.rules;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.gst.models.GstDaybookFinding;

import java.util.List;

/**
 * Strategy interface for daybook GST validation rules.
 * Implementations are pure: no repository access; all inputs arrive via the context.
 */
public interface GstDaybookRule {
    List<GstDaybookFinding> execute(GstRuleContext ctx, List<ParsedVoucher> vouchers);
}
