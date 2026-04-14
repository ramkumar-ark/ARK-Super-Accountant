import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface OrgMembership {
  organizationId: string
  organizationName: string
  role: string
  isActive: boolean
}

interface AuthUser {
  id: number
  username: string
  email: string
  role: string
  organizationId?: string
  organizationName?: string
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  organizations: OrgMembership[]
  isAuthenticated: boolean
  login: (token: string, user: AuthUser) => void
  logout: () => void
  setOrganizations: (orgs: OrgMembership[]) => void
  switchOrganization: (token: string, org: { organizationId: string; organizationName: string; role: string }) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      organizations: [],
      isAuthenticated: false,
      login: (token, user) => set({ token, user, isAuthenticated: true }),
      logout: () => set({ token: null, user: null, organizations: [], isAuthenticated: false }),
      setOrganizations: (orgs) => set({ organizations: orgs }),
      switchOrganization: (token, org) =>
        set((state) => ({
          token,
          user: state.user
            ? { ...state.user, organizationId: org.organizationId, organizationName: org.organizationName }
            : state.user,
          organizations: state.organizations.map((o) => ({
            ...o,
            isActive: o.organizationId === org.organizationId,
          })),
        })),
    }),
    { name: 'auth' }
  )
)
