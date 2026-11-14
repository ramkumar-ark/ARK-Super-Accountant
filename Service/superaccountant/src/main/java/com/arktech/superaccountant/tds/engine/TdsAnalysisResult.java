package com.arktech.superaccountant.tds.engine;

import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import com.arktech.superaccountant.tds.models.TdsFinding;

import java.util.List;

public record TdsAnalysisResult(List<TdsFinding> findings, List<TdsDeducteeEntry> entries) {
}
