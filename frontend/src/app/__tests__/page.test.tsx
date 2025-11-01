import { render, screen } from '@testing-library/react';
import HomePage from '../page';

// Mock next/navigation
jest.mock('next/navigation', () => ({
  useRouter: jest.fn(() => ({
    push: jest.fn(),
    replace: jest.fn(),
    prefetch: jest.fn(),
  })),
}));

// Mock next-auth
jest.mock('next-auth/react', () => ({
  useSession: jest.fn(() => ({
    data: null,
    status: 'unauthenticated',
  })),
  signIn: jest.fn(),
}));

// Mock auth store
jest.mock('../../store/useAuthStore', () => ({
  useAuthStore: jest.fn((selector) => {
    const state = {
      freeRemaining: 20,
      paidCredits: 0,
      setAuth: jest.fn(),
    };
    return selector ? selector(state) : state;
  }),
}));

// Mock react-dropzone
jest.mock('react-dropzone', () => ({
  useDropzone: jest.fn(() => ({
    getRootProps: () => ({ onClick: jest.fn() }),
    getInputProps: () => ({}),
    isDragActive: false,
  })),
}));

describe('HomePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render the convert page directly (no redirect)', () => {
    render(<HomePage />);
    expect(screen.getByText(/Convert Images/i)).toBeInTheDocument();
  });

  it('should display the image upload interface', () => {
    render(<HomePage />);
    expect(screen.getByText(/Drag & drop images here, or click to select/i)).toBeInTheDocument();
  });
});
