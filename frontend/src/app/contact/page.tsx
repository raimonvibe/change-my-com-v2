'use client';

import { useState } from 'react';

export default function ContactPage() {
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle');

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setStatus('submitting');

    const form = e.currentTarget;
    const formData = new FormData(form);

    try {
      const response = await fetch('https://formspree.io/f/xwplqeky', {
        method: 'POST',
        body: formData,
        headers: {
          Accept: 'application/json',
        },
      });

      if (response.ok) {
        setStatus('success');
        form.reset();
      } else {
        setStatus('error');
      }
    } catch (error) {
      setStatus('error');
    }
  };

  return (
    <div className="max-w-2xl mx-auto py-8 px-4">
      <h1 className="text-3xl font-bold mb-4">Contact Us</h1>
      <p className="text-slate-700 mb-8">
        Have a question, feedback, or need support? We&apos;d love to hear from you!
      </p>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-6 mb-8">
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Honeypot field for spam prevention */}
          <input type="text" name="_gotcha" style={{ display: 'none' }} />

          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-700 mb-2">
              Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="name"
              name="name"
              required
              className="w-full px-4 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-transparent"
              placeholder="Your name"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700 mb-2">
              Email <span className="text-red-500">*</span>
            </label>
            <input
              type="email"
              id="email"
              name="email"
              required
              className="w-full px-4 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-transparent"
              placeholder="your.email@example.com"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="subject" className="block text-sm font-medium text-slate-700 mb-2">
              Subject <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="subject"
              name="subject"
              required
              className="w-full px-4 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-transparent"
              placeholder="What is this about?"
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <label htmlFor="message" className="block text-sm font-medium text-slate-700 mb-2">
              Message <span className="text-red-500">*</span>
            </label>
            <textarea
              id="message"
              name="message"
              required
              rows={6}
              className="w-full px-4 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-transparent resize-vertical"
              placeholder="Your message..."
              disabled={status === 'submitting'}
            />
          </div>

          <div>
            <button
              type="submit"
              disabled={status === 'submitting'}
              className="w-full bg-sky-600 hover:bg-sky-700 disabled:bg-slate-400 text-white font-medium py-3 px-6 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-sky-500 focus:ring-offset-2"
            >
              {status === 'submitting' ? 'Sending...' : 'Send Message'}
            </button>
          </div>

          {status === 'success' && (
            <div className="bg-green-50 border border-green-200 text-green-800 px-4 py-3 rounded-md">
              <p className="font-medium">Message sent successfully!</p>
              <p className="text-sm mt-1">We&apos;ll get back to you as soon as possible.</p>
            </div>
          )}

          {status === 'error' && (
            <div className="bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-md">
              <p className="font-medium">Something went wrong</p>
              <p className="text-sm mt-1">Please try again or email us directly at info@raimonvibe.com</p>
            </div>
          )}
        </form>
      </div>

      <div className="bg-slate-50 rounded-lg border border-slate-200 p-6">
        <h2 className="text-xl font-semibold mb-4">Other Ways to Reach Us</h2>
        <div className="space-y-3 text-slate-700">
          <div>
            <span className="font-medium">Email:</span>{' '}
            <a href="mailto:info@raimonvibe.com" className="text-sky-600 hover:text-sky-700">
              info@raimonvibe.com
            </a>
          </div>
          <div>
            <span className="font-medium">Address:</span>
            <br />
            RaimonVibe
            <br />
            Timpaan 1-B
            <br />
            1628 MT Hoorn
            <br />
            Netherlands
          </div>
        </div>
      </div>

      <p className="text-sm text-slate-600 mt-6">
        By submitting this form, you agree to our{' '}
        <a href="/privacy" target="_blank" rel="noopener noreferrer" className="text-sky-600 hover:text-sky-700">
          Privacy Notice
        </a>
        . Your information will only be used to respond to your inquiry.
      </p>
    </div>
  );
}
