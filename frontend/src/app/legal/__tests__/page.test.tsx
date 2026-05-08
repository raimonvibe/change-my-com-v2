import React from 'react';
import { render, screen } from '@testing-library/react';
import LegalPage from '../page';

describe('LegalPage', () => {
  it('should render without crashing', () => {
    render(<LegalPage />);
    expect(screen.getByText('Legal Notice')).toBeInTheDocument();
  });

  it('should display terms of service content', () => {
    render(<LegalPage />);
    expect(screen.getAllByText(/Terms of Service/i).length).toBeGreaterThan(0);
  });

  it('should display liability information', () => {
    render(<LegalPage />);
    expect(screen.getByText(/Liability and Warranties/i)).toBeInTheDocument();
  });
});
