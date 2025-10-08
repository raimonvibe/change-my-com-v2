import { create } from 'zustand';

type AuthState = {
  email: string | null;
  authenticated: boolean;
  freeRemaining: number;
  paidCredits: number;
  setAuth: (data: Partial<AuthState>) => void;
  reset: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  email: null,
  authenticated: false,
  freeRemaining: 0,
  paidCredits: 0,
  setAuth: (data) => set((s) => ({ ...s, ...data })),
  reset: () => set({ email: null, authenticated: false, freeRemaining: 0, paidCredits: 0 }),
}));
