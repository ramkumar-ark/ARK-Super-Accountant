import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

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
    if (error.response?.status === 401) {
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

export function getGstr1Tables(workflowId: string): Promise<Gstr1Tables> {
  return api.get(`/v1/workflows/${workflowId}/gstr1/tables`).then((res) => res.data)
}

export function putGstr1FillIn(
  workflowId: string,
  fillIn: Gstr1FillInInput,
): Promise<unknown> {
  return api.put(`/v1/workflows/${workflowId}/gstr1/fill-ins`, fillIn)
}
