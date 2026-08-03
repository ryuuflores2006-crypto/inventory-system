import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Centralized Multi-Branch Inventory & Repair Portal',
  description: 'Enterprise dashboard for real-time tracking, retail sales, repair service intake, and branch transfers.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
