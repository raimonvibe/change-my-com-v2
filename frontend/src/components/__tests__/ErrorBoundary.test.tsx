import { render, screen } from '@testing-library/react'
import { ErrorBoundary } from '../ErrorBoundary'

// Component that throws an error
const ThrowError = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('Test error')
  }
  return <div>No error</div>
}

describe('ErrorBoundary Component', () => {
  // Suppress console.error for these tests
  const originalError = console.error
  beforeAll(() => {
    console.error = jest.fn()
  })

  afterAll(() => {
    console.error = originalError
  })

  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('should render children when there is no error', () => {
    render(
      <ErrorBoundary>
        <div>Test content</div>
      </ErrorBoundary>
    )

    expect(screen.getByText('Test content')).toBeInTheDocument()
  })

  it('should render error UI when an error is thrown', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    // Check for error message
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument()
  })

  it('should display error details in development mode', () => {
    const originalEnv = process.env.NODE_ENV
    process.env.NODE_ENV = 'development'

    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    // Should show error message
    expect(screen.getByText(/error/i)).toBeInTheDocument()

    process.env.NODE_ENV = originalEnv
  })

  it('should not crash the entire app when a child component errors', () => {
    const { container } = render(
      <div>
        <ErrorBoundary>
          <ThrowError shouldThrow={true} />
        </ErrorBoundary>
        <div>This should still render</div>
      </div>
    )

    expect(screen.getByText('This should still render')).toBeInTheDocument()
  })

  it('should log error to console', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    expect(console.error).toHaveBeenCalled()
  })

  it('should handle errors from different children', () => {
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>
    )

    expect(screen.getByText('No error')).toBeInTheDocument()

    // Now cause an error
    rerender(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument()
  })

  it('should provide a fallback UI', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    // Should have some user-friendly message
    const errorMessage = screen.getByText(/something went wrong/i)
    expect(errorMessage).toBeInTheDocument()
  })

  it('should have proper error boundary structure', () => {
    const { container } = render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    )

    // Should render some container for error UI
    expect(container.firstChild).toBeInTheDocument()
  })
})
