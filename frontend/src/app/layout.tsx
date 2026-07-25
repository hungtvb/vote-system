import type { Metadata } from 'next';
import './globals.scss';

export const metadata: Metadata = {
  title: 'Vote System — Ballot Edition',
  description: 'Public decision registry'
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
