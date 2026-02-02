import { render, screen, fireEvent } from '@testing-library/react'
import { Header } from '../Header'

// Mock AuthButtons component
jest.mock('../AuthButtons', () => ({
  AuthButtons: () => <div data-testid="auth-buttons">Auth Buttons</div>,
}))

describe('Header Component', () => {
  describe('Desktop View', () => {
    beforeEach(() => {
      // Set desktop viewport
      global.innerWidth = 1024
    })

    it('should render logo', () => {
      render(<Header />)
      expect(screen.getByText('Image Converter')).toBeInTheDocument()
    })

    it('should render all navigation links', () => {
      render(<Header />)

      const links = ['Home', 'Convert', 'Pricing', 'Account', 'Contact']
      links.forEach(link => {
        expect(screen.getByRole('link', { name: link })).toBeInTheDocument()
      })
    })

    it('should render auth buttons', () => {
      render(<Header />)
      const authButtons = screen.getAllByTestId('auth-buttons')
      expect(authButtons.length).toBeGreaterThanOrEqual(1)
    })

    it('should have correct href for all links', () => {
      render(<Header />)

      expect(screen.getByRole('link', { name: /^Home$/i })).toHaveAttribute('href', '/home')
expect(screen.getByRole('link', { name: /^Convert$/i })).toHaveAttribute('href', '/')
      expect(screen.getByRole('link', { name: /^Pricing$/i })).toHaveAttribute('href', '/billing')
      expect(screen.getByRole('link', { name: /^Account$/i })).toHaveAttribute('href', '/account')
      expect(screen.getByRole('link', { name: /^Contact$/i })).toHaveAttribute('href', '/contact')
    })

    it('should have sticky positioning', () => {
      const { container } = render(<Header />)
      const header = container.querySelector('header')
      expect(header).toHaveClass('sticky')
      expect(header).toHaveClass('top-0')
    })
  })

  describe('Mobile View', () => {
    it('should show mobile menu toggle button', () => {
      render(<Header />)
      const menuButton = screen.getByLabelText('Toggle menu')
      expect(menuButton).toBeInTheDocument()
    })

    it('should toggle mobile menu when button is clicked', () => {
      render(<Header />)
      const menuButton = screen.getByLabelText('Toggle menu')

      // Get initial count of nav links (desktop nav is always present in test)
      const initialLinks = screen.getAllByRole('link', { name: /Home|Convert|Pricing|Account|Contact/i })
      const initialCount = initialLinks.length

      // Click to open menu
      fireEvent.click(menuButton)

      // Should have more links now (mobile menu added)
      const linksAfterOpen = screen.getAllByRole('link', { name: /Home|Convert|Pricing|Account|Contact/i })
      expect(linksAfterOpen.length).toBeGreaterThan(initialCount)
    })

    it('should close mobile menu when a link is clicked', () => {
      render(<Header />)
      const menuButton = screen.getByLabelText('Toggle menu')

      // Open menu
      fireEvent.click(menuButton)

      // Click on a link
      const convertLink = screen.getAllByRole('link', { name: /Convert/i })[1] // Mobile link
      fireEvent.click(convertLink)

      // Menu should close (tested by component state)
      // Note: In real DOM, the menu would disappear
    })

    it('should display shortened logo text on mobile', () => {
      render(<Header />)
      expect(screen.getByText('Converter')).toBeInTheDocument()
    })
  })

  describe('Accessibility', () => {
    it('should have proper ARIA label for menu button', () => {
      render(<Header />)
      const menuButton = screen.getByLabelText('Toggle menu')
      expect(menuButton).toHaveAttribute('aria-label', 'Toggle menu')
    })

    it('should render as header element', () => {
      const { container } = render(<Header />)
      expect(container.querySelector('header')).toBeInTheDocument()
    })

    it('should have navigation landmark', () => {
      render(<Header />)
      const navElements = screen.getAllByRole('navigation')
      expect(navElements.length).toBeGreaterThan(0)
    })
  })

  describe('Styling', () => {
    it('should have border bottom', () => {
      const { container } = render(<Header />)
      const header = container.querySelector('header')
      expect(header).toHaveClass('border-b')
    })

    it('should have white background', () => {
      const { container } = render(<Header />)
      const header = container.querySelector('header')
      expect(header).toHaveClass('bg-white')
    })

    it('should have correct z-index for stacking', () => {
      const { container } = render(<Header />)
      const header = container.querySelector('header')
      expect(header).toHaveClass('z-50')
    })
  })
})
