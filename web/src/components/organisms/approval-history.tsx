'use client';

import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import HowToRegOutlinedIcon from '@mui/icons-material/HowToRegOutlined';
import SettingsSuggestOutlinedIcon from '@mui/icons-material/SettingsSuggestOutlined';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import type { ApprovalHistoryEntry, ApprovalHistoryEntryType } from '@/lib/api-types';
import { formatDateTime, formatDuration } from '@/lib/format';

const TYPE: Record<
  ApprovalHistoryEntryType,
  { label: string; color: 'default' | 'primary' | 'secondary'; icon: ReactNode }
> = {
  APPLICATION: {
    label: '申請',
    color: 'default',
    icon: <AssignmentOutlinedIcon fontSize="small" />,
  },
  APPROVAL_TASK: {
    label: '承認',
    color: 'primary',
    icon: <HowToRegOutlinedIcon fontSize="small" />,
  },
  SYSTEM_TASK: {
    label: '自動処理',
    color: 'secondary',
    icon: <SettingsSuggestOutlinedIcon fontSize="small" />,
  },
};

/**
 * 承認履歴のタイムライン。
 *
 * Flowable の履歴（誰がいつ何をしたか）をそのまま時系列で並べる。ゲートウェイや sequenceFlow は
 * サーバ側で除いてあるため、ここは受け取った順に描画するだけでよい。
 */
export function ApprovalHistory({ entries }: { entries: ApprovalHistoryEntry[] }) {
  if (entries.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        一覧の行を選ぶと、その申請の履歴を表示します。
      </Typography>
    );
  }

  return (
    <Stack spacing={1.5}>
      {entries.map((entry, index) => {
        const type = TYPE[entry.type];
        return (
          <Stack
            key={`${entry.activityId}-${entry.startedAt}-${index}`}
            direction="row"
            spacing={1.5}
          >
            {/* 左端の縦線と丸印でタイムラインに見せる */}
            <Stack sx={{ alignItems: 'center', pt: 0.5 }}>
              <Box
                sx={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'text.secondary',
                  bgcolor: 'action.hover',
                }}
              >
                {type.icon}
              </Box>
              {index < entries.length - 1 && (
                <Box sx={{ flexGrow: 1, width: '1px', bgcolor: 'divider', my: 0.5 }} />
              )}
            </Stack>

            <Paper variant="outlined" sx={{ p: 1.5, flexGrow: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" color={type.color} variant="outlined" label={type.label} />
                <Typography sx={{ fontWeight: 600 }}>{entry.name}</Typography>
                {entry.assignee !== null && (
                  <Typography variant="body2" color="text.secondary">
                    実施者: {entry.assignee}
                  </Typography>
                )}
                {entry.running && <Chip size="small" color="warning" label="実行中" />}
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {formatDateTime(entry.startedAt)}
                {entry.endedAt !== null && ` → ${formatDateTime(entry.endedAt)}`}
                {` / 所要 ${formatDuration(entry.durationMillis)}`}
              </Typography>
              {entry.comments.map(comment => (
                <Paper
                  key={`${comment.author}-${comment.at}`}
                  variant="outlined"
                  sx={{ mt: 1, p: 1, bgcolor: 'action.hover' }}
                >
                  <Typography variant="body2">{comment.message}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {comment.author} / {formatDateTime(comment.at)}
                  </Typography>
                </Paper>
              ))}
            </Paper>
          </Stack>
        );
      })}
    </Stack>
  );
}
