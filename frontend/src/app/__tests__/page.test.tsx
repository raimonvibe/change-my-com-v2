import { redirect } from 'next/navigation';
import HomePage from '../page';

// Mock next/navigation
jest.mock('next/navigation', () => ({
  redirect: jest.fn(),
}));

describe('HomePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should redirect to /convert', () => {
    HomePage();
    expect(redirect).toHaveBeenCalledWith('/convert');
  });

  it('should redirect immediately without rendering', () => {
    const result = HomePage();
    expect(redirect).toHaveBeenCalledTimes(1);
    expect(result).toBeUndefined();
  });
});
