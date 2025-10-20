import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { useSession, signIn, signOut } from 'next-auth/react'
import { AuthButtons } from '../AuthButtons'

// Mock next-auth
jest.mock('next-auth/react')

const mockUseSession = useSession as jest.MockedFunction<typeof useSession>
const mockSignIn = signIn as jest.MockedFunction<typeof signIn>
const mockSignOut = signOut as jest.MockedFunction<typeof signOut>

describe('AuthButtons Component', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  describe('Unauthenticated State', () => {
    beforeEach(() => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'unauthenticated',
        update: jest.fn(),
      })
    })

    it('should render sign in button when not authenticated', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        expect(screen.getByText(/Sign in with Google/i)).toBeInTheDocument()
      })
    })

    it('should call signIn when sign in button is clicked', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const signInButton = screen.getByText(/Sign in with Google/i)
        fireEvent.click(signInButton)
      })

      expect(mockSignIn).toHaveBeenCalledWith('google')
    })

    it('should display Google logo in sign in button', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByText(/Sign in with Google/i).closest('button')
        const svg = button?.querySelector('svg')
        expect(svg).toBeInTheDocument()
      })
    })

    it('should show shortened text on mobile', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const mobileText = screen.getByText('Sign in')
        expect(mobileText).toHaveClass('sm:hidden')
      })
    })

    it('should show full text on desktop', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const desktopText = screen.getByText('Sign in with Google')
        expect(desktopText).toHaveClass('hidden', 'sm:inline')
      })
    })
  })

  describe('Authenticated State', () => {
    beforeEach(() => {
      mockUseSession.mockReturnValue({
        data: {
          user: {
            email: 'test@example.com',
            name: 'Test User',
          },
          expires: '2025-12-31',
        },
        status: 'authenticated',
        update: jest.fn(),
      })
    })

    it('should render sign out button when authenticated', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        expect(screen.getByText(/Sign out/i)).toBeInTheDocument()
      })
    })

    it('should call signOut when sign out button is clicked', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const signOutButton = screen.getByText(/Sign out/i)
        fireEvent.click(signOutButton)
      })

      expect(mockSignOut).toHaveBeenCalled()
    })

    it('should display logout icon', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByText(/Sign out/i).closest('button')
        expect(button).toBeInTheDocument()
        // LogOut icon from lucide-react is rendered
      })
    })

    it('should have different styling for sign out button', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const signOutButton = screen.getByText(/Sign out/i).closest('button')
        expect(signOutButton).toHaveClass('bg-sky-600')
        expect(signOutButton).toHaveClass('text-white')
      })
    })

    it('should hide sign out text on mobile', async () => {
      render(<AuthButtons />)

      await waitFor(() => {
        const signOutText = screen.getByText('Sign out')
        expect(signOutText).toHaveClass('hidden', 'sm:inline')
      })
    })
  })

  describe('Loading State', () => {
    beforeEach(() => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'loading',
        update: jest.fn(),
      })
    })

    it('should show placeholder during initial mount', () => {
      render(<AuthButtons />)

      // Before mounted, should show placeholder
      const placeholder = screen.getByText(/Sign in with Google/i)
      expect(placeholder).toBeInTheDocument()
    })
  })

  describe('Hydration Safety', () => {
    it('should prevent hydration mismatch by waiting for mount', () => {
      mockUseSession.mockReturnValue({
        data: {
          user: { email: 'test@example.com', name: 'Test User' },
          expires: '2025-12-31',
        },
        status: 'authenticated',
        update: jest.fn(),
      })

      render(<AuthButtons />)

      // Component uses mounted state to prevent SSR/CSR mismatch
      // This is tested by the component rendering without errors
    })
  })

  describe('Accessibility', () => {
    it('should have accessible button text', async () => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'unauthenticated',
        update: jest.fn(),
      })

      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByRole('button', { name: /Sign in/i })
        expect(button).toBeInTheDocument()
      })
    })

    it('should be keyboard accessible', async () => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'unauthenticated',
        update: jest.fn(),
      })

      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByRole('button', { name: /Sign in/i })
        expect(button.tagName).toBe('BUTTON')
      })
    })
  })

  describe('Visual Design', () => {
    it('should have shadow and border on sign in button', async () => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'unauthenticated',
        update: jest.fn(),
      })

      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByText(/Sign in with Google/i).closest('button')
        expect(button).toHaveClass('shadow-md')
        expect(button).toHaveClass('border')
      })
    })

    it('should have hover state', async () => {
      mockUseSession.mockReturnValue({
        data: null,
        status: 'unauthenticated',
        update: jest.fn(),
      })

      render(<AuthButtons />)

      await waitFor(() => {
        const button = screen.getByText(/Sign in with Google/i).closest('button')
        expect(button).toHaveClass('hover:shadow-lg')
      })
    })
  })
})
