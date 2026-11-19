export interface PortalDownloadInfo {
  sha256: string
  bytes: number
  writerVersion: string
  parts: number
}

export function latestPortalDownload(
  events: Array<{ kind: string; payload: string | null }>,
): PortalDownloadInfo | null {
  for (let i = events.length - 1; i >= 0; i--) {
    const event = events[i]
    if (event.kind !== 'PORTAL_FILE_DOWNLOADED' || event.payload == null) continue
    try {
      const parsed = JSON.parse(event.payload) as PortalDownloadInfo
      if (typeof parsed.sha256 === 'string' && parsed.sha256.length > 0) return parsed
    } catch {
      continue
    }
  }
  return null
}
