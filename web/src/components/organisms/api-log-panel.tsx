'use client';

import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { ApiLogItem } from '@/components/molecules/api-log-item';
import type { ApiLogEntry } from '@/lib/api-types';

interface Props {
  entries: ApiLogEntry[];
  onClear: () => void;
}

/** 直近の API 呼び出しと生のレスポンスを表示する。何が起きたかを追えるようにするためのパネル。 */
export function ApiLogPanel({ entries, onClear }: Props) {
  return (
    <Stack spacing={1}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="body2" color="text.secondary">
          直近 {entries.length} 件
        </Typography>
        <Button size="small" variant="outlined" onClick={onClear} disabled={entries.length === 0}>
          クリア
        </Button>
      </Stack>
      {entries.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          まだ API を呼び出していません。
        </Typography>
      ) : (
        <Stack spacing={1} sx={{ maxHeight: 420, overflowY: 'auto' }}>
          {entries.map(entry => (
            <ApiLogItem key={entry.id} entry={entry} />
          ))}
        </Stack>
      )}
    </Stack>
  );
}
