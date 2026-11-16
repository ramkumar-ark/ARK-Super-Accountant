package com.arktech.superaccountant.filedreturns.parse;

import com.arktech.superaccountant.filedreturns.FiledReturnLine;

import java.util.List;

public record Gstr1Parsed(String gstin, String filingPeriod, List<FiledReturnLine> lines, List<String> warnings) {
}
