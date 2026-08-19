'use client';

import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { StatusChip } from '@/components/atoms/status-chip';
import type { ExpenseRequest } from '@/lib/api-types';
import { formatDateTime, formatYen } from '@/lib/format';

interface Props {
  requests: ExpenseRequest[];
  selectedId: string | null;
  onSelect: (request: ExpenseRequest) => void;
  onFireReminders: (request: ExpenseRequest) => void;
}

/** 自分の申請一覧。プロセス変数として持っている値をそのまま確認できるようにしている。 */
export function RequestTable({ requests, selectedId, onSelect, onFireReminders }: Props) {
  if (requests.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        まだ申請がありません。
      </Typography>
    );
  }

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>件名</TableCell>
            <TableCell align="right">金額</TableCell>
            <TableCell>状態</TableCell>
            <TableCell>承認待ちタスク</TableCell>
            <TableCell>承認者</TableCell>
            <TableCell>伝票番号</TableCell>
            <TableCell align="right">リマインド</TableCell>
            <TableCell>申請日時</TableCell>
            <TableCell>操作</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {requests.map(request => (
            <TableRow
              key={request.processInstanceId}
              hover
              selected={request.processInstanceId === selectedId}
              sx={{ cursor: 'pointer' }}
              onClick={() => onSelect(request)}
            >
              <TableCell>{request.title}</TableCell>
              <TableCell align="right">{formatYen(request.amount)}</TableCell>
              <TableCell>
                <StatusChip status={request.status} />
              </TableCell>
              <TableCell>{request.currentTaskName ?? '-'}</TableCell>
              <TableCell>{request.approverId ?? '-'}</TableCell>
              <TableCell>{request.erpVoucherNo ?? '-'}</TableCell>
              <TableCell align="right">{request.reminderCount}</TableCell>
              <TableCell>{formatDateTime(request.startedAt)}</TableCell>
              <TableCell>
                <Button
                  size="small"
                  variant="outlined"
                  disabled={request.status !== 'IN_PROGRESS'}
                  title="3日待たずにリマインドタイマーを発火させる"
                  onClick={event => {
                    event.stopPropagation();
                    onFireReminders(request);
                  }}
                >
                  リマインド発火
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
