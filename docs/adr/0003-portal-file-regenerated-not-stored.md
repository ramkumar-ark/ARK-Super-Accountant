# ADR 0003: The portal file is regenerated, not stored

Status: proposed

The portal file is a pure function of the frozen prepared return and a pinned writer version. We keep its SHA-256, size and writer version in the download and upload events, and no file bytes. This avoids deciding GST documents storage, which is on hold. Consequence: the writer version must be immutable per released schema, and a writer change needs a new version, not an edit.
