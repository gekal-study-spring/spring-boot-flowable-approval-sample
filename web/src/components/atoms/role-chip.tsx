import Chip from '@mui/material/Chip';
import type { ApproverRole } from '@/lib/api-types';

const ROLE: Record<ApproverRole, { label: string; color: 'primary' | 'secondary' }> = {
  MANAGER: { label: '課長承認', color: 'primary' },
  DIRECTOR: { label: '部長承認', color: 'secondary' },
};

/** 承認者ロール（BPMN の candidateGroups に対応）を表すチップ。 */
export function RoleChip({ role }: { role: ApproverRole }) {
  const { label, color } = ROLE[role];
  return <Chip size="small" color={color} label={label} />;
}
