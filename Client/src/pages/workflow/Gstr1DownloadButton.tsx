import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiErrorMessage } from '@/lib/apiError'
import { getGstr1Export } from '@/lib/api'

const buttonClass =
  'inline-flex h-9 w-fit items-center justify-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-3 text-sm font-medium text-white hover:bg-[var(--color-primary-hover)] disabled:cursor-not-allowed disabled:opacity-50'

interface Props {
  workflowId: string
  disabled?: boolean
  disabledReason?: string
}

function fileNameFrom(disposition: string | undefined | null): string | null {
  if (!disposition) return null
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition)
  if (!match) return null
  try {
    return decodeURIComponent(match[1] ?? match[2] ?? '')
  } catch {
    return match[2] ?? match[1] ?? null
  }
}

export function Gstr1DownloadButton({ workflowId, disabled, disabledReason }: Props) {
  const [error, setError] = useState('')
  const queryClient = useQueryClient()

  const downloadMutation = useMutation({
    mutationFn: () => getGstr1Export(workflowId),
    onSuccess: (response) => {
      setError('')
      const blob = response.data as Blob
      const fileName =
        fileNameFrom(response.headers?.['content-disposition'] as string | undefined) ??
        'portal-file.json'
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      queryClient.invalidateQueries({ queryKey: ['workflow-events', workflowId] })
    },
    onError: (err) => setError(apiErrorMessage(err, 'Could not download the portal file.')),
  })

  return (
    <div className="flex flex-col items-start gap-1">
      <button
        type="button"
        className={buttonClass}
        disabled={disabled || downloadMutation.isPending}
        title={disabled ? disabledReason : undefined}
        onClick={() => downloadMutation.mutate()}
      >
        {downloadMutation.isPending ? 'Preparing download…' : 'Download portal file'}
      </button>
      {error && (
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          {error}
        </p>
      )}
    </div>
  )
}
