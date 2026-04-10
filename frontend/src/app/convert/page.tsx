import { redirect } from 'next/navigation';
import type { Metadata } from 'next';

// SEO: This URL redirects to /. Tell crawlers not to index it.
export const metadata: Metadata = {
  robots: { index: false, follow: true },
};

export default function ConvertPage() {
  redirect('/');
}
