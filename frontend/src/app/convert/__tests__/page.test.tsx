import { redirect } from 'next/navigation';
import ConvertPage from '../page';

// Mock next/navigation
jest.mock('next/navigation', () => ({
  redirect: jest.fn(),
}));

describe('ConvertPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should redirect to / (root)', () => {
    ConvertPage();
    expect(redirect).toHaveBeenCalledWith('/');
  });

  it('should redirect immediately without rendering', () => {
    const result = ConvertPage();
    expect(redirect).toHaveBeenCalledTimes(1);
    expect(result).toBeUndefined();
  });
});
