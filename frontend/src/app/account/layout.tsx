import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Your Account | Change My',
  alternates: {
    canonical: 'https://www.change-my.com/account',
  },
  robots: {
    index: false,
    follow: false,
  },
};

export default function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <>{children}</>;
}
