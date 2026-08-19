'use client';

import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import type { ExpenseRequest } from '@/lib/api-types';
import { formatDateTime, formatYen } from '@/lib/format';

/** 選択した申請の全項目（= プロセス変数の中身）を表示する。 */
export function RequestDetail({ request }: { request: ExpenseRequest | null }) {
  if (request === null) {
    return (
      <Typography variant="body2" color="text.secondary">
        一覧の行を選ぶと、プロセス変数の中身を表示します。
      </Typography>
    );
  }

  const rows: [string, string][] = [
    ['プロセスインスタンスID', request.processInstanceId],
    ['申請者 (applicantId)', request.applicantId],
    ['件名 (title)', request.title],
    ['金額 (amount)', formatYen(request.amount)],
    ['支出日 (expenseDate)', request.expenseDate],
    ['費目 (category)', request.category],
    ['備考 (remarks)', request.remarks ?? '-'],
    ['状態 (status)', request.status],
    ['承認待ちタスク (currentTaskName)', request.currentTaskName ?? '-'],
    ['承認者 (approverId)', request.approverId ?? '-'],
    ['承認コメント (approvalComment)', request.approvalComment ?? '-'],
    ['伝票番号 (erpVoucherNo)', request.erpVoucherNo ?? '-'],
    ['リマインド回数 (reminderCount)', String(request.reminderCount)],
    ['申請日時 (startedAt)', formatDateTime(request.startedAt)],
    ['完了日時 (endedAt)', formatDateTime(request.endedAt)],
  ];

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableBody>
          {rows.map(([label, value]) => (
            <TableRow key={label}>
              <TableCell sx={{ width: 280, color: 'text.secondary' }}>{label}</TableCell>
              <TableCell sx={{ whiteSpace: 'normal', wordBreak: 'break-all' }}>{value}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
