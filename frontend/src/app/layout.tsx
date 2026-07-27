import type { Metadata, Viewport } from 'next';
import './globals.scss';

export const metadata: Metadata = {
  title: 'Vote System — Ballot Edition',
  description: 'Public decision registry',
  manifest: '/manifest.webmanifest',
  icons: {
    icon: [{ url: '/ballot-mark.svg', type: 'image/svg+xml', sizes: 'any' }],
    shortcut: ['/ballot-mark.svg'],
    apple: [{ url: '/ballot-mark.svg', type: 'image/svg+xml', sizes: '180x180' }]
  }
};

export const viewport: Viewport = {
  themeColor: '#1E2A3A'
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
