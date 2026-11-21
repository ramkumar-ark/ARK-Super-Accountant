import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** Set by session validation: let the caller (router guard) handle the redirect instead of the global interceptor. */
    skipAuthRedirect?: boolean
  }
}

export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.skipAuthRedirect) {
      useAuthStore.getState().logout()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export interface Gstr1TableSummary {
  tableCode: string
  rowCount: number
  totals: Record<string, number>
}

export interface Gstr1FillInSummary {
  scope: string
  key: string
  attribute: string
  value: string | null
  enteredBy: string
  enteredAt: string
  fixedInPrepare: boolean
}

export interface Gstr1Tables {
  workflowId: string
  stage: string
  builtAt: string | null
  frozenAt: string | null
  tables: Gstr1TableSummary[]
  fillIns: Gstr1FillInSummary[]
}

export interface Gstr1FillInInput {
  scope: string
  key: string
  attribute: string
  value: string
}

export interface Gstr1ValidationIssue {
  code: string
  severity: 'ERROR' | 'WARNING'
  tableCode: string | null
  rowKey: string | null
  message: string
}

export interface Gstr1Validation {
  errors: Gstr1ValidationIssue[]
  warnings: Gstr1ValidationIssue[]
}

export function getGstr1Validation(workflowId: string): Promise<Gstr1Validation> {
  return api.get(`/v1/workflows/${workflowId}/gstr1/validation`).then((res) => res.data)
}

export function getGstr1Export(workflowId: string) {
  return api.get(`/v1/workflows/${workflowId}/gstr1/export`, { responseType: 'blob' })
}

export function getGstr1Tables(workflowId: string): Promise<Gstr1Tables> {
  return api.get(`/v1/workflows/${workflowId}/gstr1/tables`).then((res) => res.data)
}

export function putGstr1FillIn(
  workflowId: string,
  fillIn: Gstr1FillInInput,
): Promise<unknown> {
  return api.put(`/v1/workflows/${workflowId}/gstr1/fill-ins`, fillIn)
}
