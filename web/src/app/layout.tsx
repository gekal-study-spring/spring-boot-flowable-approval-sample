import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { MuiProvider } from '@/components/providers/mui-provider';
import { SITE } from '@/config/site';
import './globals.css';

export const metadata: Metadata = {
  title: { default: SITE.name, template: `%s | ${SITE.name}` },
  description: SITE.description,
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ja">
      <body>
        <MuiProvider>{children}</MuiProvider>
      </body>
    </html>
  );
}
