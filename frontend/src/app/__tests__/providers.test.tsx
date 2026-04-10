import React from 'react';
import { render, screen } from '@testing-library/react';
import { SessionProvider } from 'next-auth/react';
import { Providers } from '../providers';

// Mock next-auth
jest.mock('next-auth/react', () => ({
  SessionProvider: jest.fn(({ children }) => <div data-testid="session-provider">{children}</div>),
}));

describe('Providers Component', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render children', () => {
    render(
      <Providers>
        <div>Test Child</div>
      </Providers>
    );

    expect(screen.getByText('Test Child')).toBeInTheDocument();
  });

  it('should wrap children with SessionProvider', () => {
    render(
      <Providers>
        <div>Test Child</div>
      </Providers>
    );

    expect(SessionProvider).toHaveBeenCalled();
    expect(screen.getByTestId('session-provider')).toBeInTheDocument();
  });

  it('should pass children to SessionProvider', () => {
    const testContent = 'Session Provider Test';
    render(
      <Providers>
        <span>{testContent}</span>
      </Providers>
    );

    expect(screen.getByText(testContent)).toBeInTheDocument();
  });

  it('should render multiple children', () => {
    render(
      <Providers>
        <div>Child 1</div>
        <div>Child 2</div>
        <div>Child 3</div>
      </Providers>
    );

    expect(screen.getByText('Child 1')).toBeInTheDocument();
    expect(screen.getByText('Child 2')).toBeInTheDocument();
    expect(screen.getByText('Child 3')).toBeInTheDocument();
  });
});
