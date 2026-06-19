import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ContactPage from '../page';

describe('ContactPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('Page Rendering', () => {
    it('should render contact page', () => {
      render(<ContactPage />);
      expect(screen.getByText('Contact Us')).toBeInTheDocument();
    });

    it('should render contact form', () => {
      render(<ContactPage />);
      expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/subject/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/message/i)).toBeInTheDocument();
    });

    it('should have a submit button', () => {
      render(<ContactPage />);
      expect(screen.getByRole('button', { name: /send message/i })).toBeInTheDocument();
    });

    it('should have honeypot field for spam prevention', () => {
      const { container } = render(<ContactPage />);
      const honeypot = container.querySelector('input[name="_gotcha"]');
      expect(honeypot).toBeInTheDocument();
      expect(honeypot).toHaveClass('hidden');
      expect(honeypot).toHaveAttribute('tabindex', '-1');
    });
  });

  describe('Form Validation', () => {
    it('should mark required fields', () => {
      render(<ContactPage />);
      expect(screen.getByLabelText(/name/i)).toBeRequired();
      expect(screen.getByLabelText(/email/i)).toBeRequired();
      expect(screen.getByLabelText(/subject/i)).toBeRequired();
      expect(screen.getByLabelText(/message/i)).toBeRequired();
    });

    it('should have email input type', () => {
      render(<ContactPage />);
      const emailInput = screen.getByLabelText(/email/i);
      expect(emailInput).toHaveAttribute('type', 'email');
    });
  });

  describe('Form Submission', () => {
    it('should submit form successfully', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: true,
      });

      render(<ContactPage />);

      fireEvent.change(screen.getByLabelText(/name/i), {
        target: { value: 'John Doe' },
      });
      fireEvent.change(screen.getByLabelText(/email/i), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByLabelText(/subject/i), {
        target: { value: 'Test Subject' },
      });
      fireEvent.change(screen.getByLabelText(/message/i), {
        target: { value: 'Test message content' },
      });

      const submitButton = screen.getByRole('button', { name: /send message/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          'https://formspree.io/f/xwplqeky',
          expect.objectContaining({
            method: 'POST',
            headers: {
              Accept: 'application/json',
            },
          })
        );
      });

      await waitFor(() => {
        expect(screen.getByText(/message sent successfully/i)).toBeInTheDocument();
      });
    });

    it('should show error message on submission failure', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: false,
      });

      render(<ContactPage />);

      fireEvent.change(screen.getByLabelText(/name/i), {
        target: { value: 'John Doe' },
      });
      fireEvent.change(screen.getByLabelText(/email/i), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByLabelText(/subject/i), {
        target: { value: 'Test Subject' },
      });
      fireEvent.change(screen.getByLabelText(/message/i), {
        target: { value: 'Test message' },
      });

      fireEvent.click(screen.getByRole('button', { name: /send message/i }));

      await waitFor(() => {
        expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      });
    });

    it('should handle network error', async () => {
      (global.fetch as jest.Mock).mockRejectedValueOnce(new Error('Network error'));

      render(<ContactPage />);

      fireEvent.change(screen.getByLabelText(/name/i), {
        target: { value: 'John Doe' },
      });
      fireEvent.change(screen.getByLabelText(/email/i), {
        target: { value: 'john@example.com' },
      });
      fireEvent.change(screen.getByLabelText(/subject/i), {
        target: { value: 'Test' },
      });
      fireEvent.change(screen.getByLabelText(/message/i), {
        target: { value: 'Test' },
      });

      fireEvent.click(screen.getByRole('button', { name: /send message/i }));

      await waitFor(() => {
        expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
      });
    });

    it('should show submitting state', async () => {
      (global.fetch as jest.Mock).mockImplementation(
        () => new Promise(resolve => setTimeout(() => resolve({ ok: true }), 100))
      );

      render(<ContactPage />);

      fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'John' } });
      fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'john@example.com' } });
      fireEvent.change(screen.getByLabelText(/subject/i), { target: { value: 'Test' } });
      fireEvent.change(screen.getByLabelText(/message/i), { target: { value: 'Test' } });

      fireEvent.click(screen.getByRole('button', { name: /send message/i }));

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /sending/i })).toBeInTheDocument();
      });
    });

    it('should disable form fields while submitting', async () => {
      (global.fetch as jest.Mock).mockImplementation(
        () => new Promise(resolve => setTimeout(() => resolve({ ok: true }), 100))
      );

      render(<ContactPage />);

      fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'John' } });
      fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'john@example.com' } });
      fireEvent.change(screen.getByLabelText(/subject/i), { target: { value: 'Test' } });
      fireEvent.change(screen.getByLabelText(/message/i), { target: { value: 'Test' } });

      fireEvent.click(screen.getByRole('button', { name: /send message/i }));

      await waitFor(() => {
        expect(screen.getByLabelText(/name/i)).toBeDisabled();
        expect(screen.getByLabelText(/email/i)).toBeDisabled();
        expect(screen.getByLabelText(/subject/i)).toBeDisabled();
        expect(screen.getByLabelText(/message/i)).toBeDisabled();
      });
    });

    it('should reset form after successful submission', async () => {
      (global.fetch as jest.Mock).mockResolvedValueOnce({
        ok: true,
      });

      render(<ContactPage />);

      const nameInput = screen.getByLabelText(/name/i) as HTMLInputElement;
      const emailInput = screen.getByLabelText(/email/i) as HTMLInputElement;
      const subjectInput = screen.getByLabelText(/subject/i) as HTMLInputElement;
      const messageInput = screen.getByLabelText(/message/i) as HTMLTextAreaElement;

      fireEvent.change(nameInput, { target: { value: 'John Doe' } });
      fireEvent.change(emailInput, { target: { value: 'john@example.com' } });
      fireEvent.change(subjectInput, { target: { value: 'Test Subject' } });
      fireEvent.change(messageInput, { target: { value: 'Test message' } });

      fireEvent.click(screen.getByRole('button', { name: /send message/i }));

      await waitFor(() => {
        expect(screen.getByText(/message sent successfully/i)).toBeInTheDocument();
      });

      // Form should be reset
      await waitFor(() => {
        expect(nameInput.value).toBe('');
        expect(emailInput.value).toBe('');
        expect(subjectInput.value).toBe('');
        expect(messageInput.value).toBe('');
      });
    });
  });

  describe('Contact Information Display', () => {
    it('should have privacy policy link', () => {
      render(<ContactPage />);
      const privacyLink = screen.getByRole('link', { name: /privacy notice/i });
      expect(privacyLink).toHaveAttribute('href', '/privacy');
      expect(privacyLink).toHaveAttribute('target', '_blank');
    });
  });
});
