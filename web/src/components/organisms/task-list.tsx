'use client';

import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { RoleChip } from '@/components/atoms/role-chip';
import type { ApprovalTask } from '@/lib/api-types';
import { formatDateTime, formatYen } from '@/lib/format';

interface Props {
  tasks: ApprovalTask[];
  onDecide: (task: ApprovalTask, decision: 'approve' | 'reject', comment: string) => Promise<void>;
}

/** 自分（の所属グループ）が処理できる承認タスク一覧。 */
export function TaskList({ tasks, onDecide }: Props) {
  const [comments, setComments] = useState<Record<string, string>>({});
  const [busyTaskId, setBusyTaskId] = useState<string | null>(null);

  const decide = async (task: ApprovalTask, decision: 'approve' | 'reject') => {
    setBusyTaskId(task.taskId);
    try {
      await onDecide(task, decision, comments[task.taskId] ?? '');
      setComments(current => ({ ...current, [task.taskId]: '' }));
    } finally {
      setBusyTaskId(null);
    }
  };

  if (tasks.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        処理できる承認タスクはありません。承認権限のあるユーザー（佐藤・田中）でログインしてください。
      </Typography>
    );
  }

  return (
    <Stack spacing={1.5}>
      {tasks.map(task => (
        <Paper key={task.taskId} variant="outlined" sx={{ p: 1.5 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <RoleChip role={task.role} />
            <Typography sx={{ fontWeight: 600 }}>{task.title}</Typography>
            <Typography sx={{ ml: 'auto' }}>{formatYen(task.amount)}</Typography>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            申請者: {task.applicantId} / 発生日時: {formatDateTime(task.createdAt)} / 引き受け:{' '}
            {task.assignee ?? '未引き受け'}
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mt: 1 }}>
            <TextField
              fullWidth
              size="small"
              placeholder="コメント（却下時は必須）"
              value={comments[task.taskId] ?? ''}
              onChange={event =>
                setComments(current => ({ ...current, [task.taskId]: event.target.value }))
              }
            />
            <Button
              variant="contained"
              disabled={busyTaskId === task.taskId}
              onClick={() => decide(task, 'approve')}
            >
              承認
            </Button>
            <Button
              variant="outlined"
              color="error"
              disabled={busyTaskId === task.taskId}
              onClick={() => decide(task, 'reject')}
            >
              却下
            </Button>
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}
