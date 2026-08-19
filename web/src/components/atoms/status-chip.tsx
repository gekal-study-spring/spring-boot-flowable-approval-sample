import Chip from '@mui/material/Chip';
import type { ExpenseRequestStatus } from '@/lib/api-types';

const STATUS: Record<
  ExpenseRequestStatus,
  { label: string; color: 'warning' | 'success' | 'error' }
> = {
  IN_PROGRESS: { label: '承認待ち', color: 'warning' },
  APPROVED: { label: '承認済み', color: 'success' },
  REJECTED: { label: '却下', color: 'error' },
};

/** 申請の進行状況を表すチップ。 */
export function StatusChip({ status }: { status: ExpenseRequestStatus }) {
  const { label, color } = STATUS[status];
  return <Chip size="small" variant="outlined" color={color} label={label} />;
}
