'use client';

import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ApiLogEntry } from '@/lib/api-types';

const PRE_STYLE = {
  m: 0,
  mt: 0.5,
  p: 1,
  borderRadius: 1,
  bgcolor: 'grey.100',
  fontSize: 12,
  overflowX: 'auto',
  maxHeight: 220,
} as const;

/** API 呼び出し1件分の記録。リクエスト本文とレスポンス本文をそのまま出す。 */
export function ApiLogItem({ entry }: { entry: ApiLogEntry }) {
  return (
    <Paper variant="outlined" sx={{ p: 1 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip
          size="small"
          color={entry.ok ? 'success' : 'error'}
          variant="outlined"
          label={entry.status}
        />
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {entry.method}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ wordBreak: 'break-all' }}>
          {entry.path}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
          {entry.at}
        </Typography>
      </Stack>
      {entry.requestBody !== null && (
        <Typography component="pre" sx={{ ...PRE_STYLE, bgcolor: 'primary.50' }}>
          {JSON.stringify(entry.requestBody, null, 2)}
        </Typography>
      )}
      <Typography component="pre" sx={PRE_STYLE}>
        {JSON.stringify(entry.responseBody, null, 2)}
      </Typography>
    </Paper>
  );
}
