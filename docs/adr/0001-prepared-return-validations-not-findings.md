# ADR 0001: Prepared-return validations are not findings

Status: proposed

Workflow spec §7 said pre-export checks become GSTR-1-scoped day-book findings. We instead run them as validation results on the prepared return (errors block the portal file and Finalize; warnings are advisory), because they depend on fill-ins and on the assembled tables, not on a day-book voucher alone, and a finding cannot be cleared by a fill-in. Day-book rules stay findings. Cost: two review surfaces in Prepare.
