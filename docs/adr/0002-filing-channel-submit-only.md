# ADR 0002: The filing channel only submits; the upload is an event, not a stage

Status: proposed

`Gstr1FilingChannel` takes a prepared return and returns a status and reference. The spec's wider `PortalGateway` (IMS, 2B and 3B-draft fetches) is not reused here. A portal upload is logged as a non-stage event (`PORTAL_UPLOADED`); the stage line stays FINALIZED then FILED, and Filed carries a channel tag (`PORTAL_FILE`, later `GSP`). Considered: adding an UPLOADED stage (rejected: forces a stage migration and every dependency check to learn it).
