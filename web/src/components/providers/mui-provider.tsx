'use client';

import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter';
import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';

// 日本語が主体の画面なので、和文フォントを先に並べる
const theme = createTheme({
  palette: { mode: 'light', primary: { main: '#1f6feb' } },
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Hiragino Sans"',
      '"Noto Sans JP"',
      'sans-serif',
    ].join(','),
    fontSize: 13,
  },
  components: {
    MuiTableCell: { styleOverrides: { root: { whiteSpace: 'nowrap' } } },
  },
});

/** MUI のテーマと Emotion のキャッシュを供給する。'use client' はこの葉に閉じ込める。 */
export function MuiProvider({ children }: { children: ReactNode }) {
  return (
    <AppRouterCacheProvider options={{ key: 'mui' }}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </AppRouterCacheProvider>
  );
}
