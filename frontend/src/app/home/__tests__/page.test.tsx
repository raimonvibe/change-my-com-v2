import React from 'react';
import { render, screen } from '@testing-library/react';
import HomePage from '../page';

// Mock Next.js Link component
jest.mock('next/link', () => {
  const MockLink = ({ children, href }: { children: React.ReactNode; href: string }) => {
    return <a href={href}>{children}</a>;
  };
  MockLink.displayName = 'Link';
  return MockLink;
});

describe('HomePage', () => {
  it('should render without crashing', () => {
    render(<HomePage />);
    expect(screen.getByText('Image Converter')).toBeInTheDocument();
  });

  it('should have a link to converter', () => {
    render(<HomePage />);
    const link = screen.getByText('Try converter');
    expect(link.closest('a')).toHaveAttribute('href', '/convert');
  });
});
