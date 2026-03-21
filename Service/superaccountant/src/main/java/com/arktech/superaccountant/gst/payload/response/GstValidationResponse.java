package com.arktech.superaccountant.gst.payload.response;

import java.util.List;
import java.util.Map;

import com.arktech.superaccountant.gst.models.GstValidationResult;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GstValidationResponse {
    private int totalVouchersAnalyzed;
    private int vouchersWithErrors;
    private Map<String, Integer> errorTypeCounts;
    private List<GstValidationResult> results;
}
