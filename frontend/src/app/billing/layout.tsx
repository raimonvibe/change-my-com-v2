import type { Metadata } from "next";

export const metadata: Metadata = {
  alternates: {
    canonical: 'https://www.change-my.com/billing',
  },
};

export default function BillingLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

