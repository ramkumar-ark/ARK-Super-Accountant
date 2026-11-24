export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: string; message?: string } } })?.response
    ?.data
  return data?.error || data?.message || fallback
}
