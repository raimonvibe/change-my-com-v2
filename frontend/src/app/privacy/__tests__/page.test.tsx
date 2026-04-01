import React from 'react';
import { render, screen } from '@testing-library/react';
import PrivacyPage from '../page';

describe('PrivacyPage', () => {
  it('should render without crashing', () => {
    render(<PrivacyPage />);
    expect(screen.getByText('Privacy Notice')).toBeInTheDocument();
  });

  it('should display GDPR rights information', () => {
    render(<PrivacyPage />);
    expect(screen.getByText(/Your Rights.*GDPR/i)).toBeInTheDocument();
  });

  it('should explain data collection', () => {
    render(<PrivacyPage />);
    expect(screen.getByText(/Data Collection and Processing/i)).toBeInTheDocument();
  });
});
