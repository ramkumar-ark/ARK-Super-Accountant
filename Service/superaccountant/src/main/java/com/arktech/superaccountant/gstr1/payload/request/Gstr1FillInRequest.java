package com.arktech.superaccountant.gstr1.payload.request;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Gstr1FillInRequest {

    @NotNull
    private Gstr1FillInScope scope;

    @NotBlank
    @Size(max = 512)
    private String key;

    @NotNull
    private Gstr1FillInAttribute attribute;

    @Size(max = 1024)
    private String value;
}
