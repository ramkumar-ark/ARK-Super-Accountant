package com.arktech.superaccountant.gstr1.payload.response;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Gstr1FillInResponse {

    private UUID id;

    private Gstr1FillInScope scope;

    private String key;

    private Gstr1FillInAttribute attribute;

    private String value;

    private String enteredBy;

    private Instant enteredAt;
}
