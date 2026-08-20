/** API のレスポンス型。サーバ側の DTO と1対1で対応する。 */

export type ExpenseRequestStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';

export type ApproverRole = 'MANAGER' | 'DIRECTOR';

export interface ExpenseRequest {
  processInstanceId: string;
  applicantId: string;
  title: string;
  amount: number;
  expenseDate: string;
  category: string;
  remarks: string | null;
  status: ExpenseRequestStatus;
  currentTaskName: string | null;
  approverId: string | null;
  approvalComment: string | null;
  erpVoucherNo: string | null;
  reminderCount: number;
  startedAt: string;
  endedAt: string | null;
}

export interface ApprovalTask {
  taskId: string;
  name: string;
  role: ApproverRole;
  processInstanceId: string;
  assignee: string | null;
  createdAt: string;
  title: string;
  amount: number;
  applicantId: string;
}

export type ApprovalHistoryEntryType = 'APPLICATION' | 'APPROVAL_TASK' | 'SYSTEM_TASK';

export interface ApprovalComment {
  author: string;
  message: string;
  at: string;
}

export interface ApprovalHistoryEntry {
  type: ApprovalHistoryEntryType;
  name: string;
  activityId: string;
  assignee: string | null;
  startedAt: string;
  endedAt: string | null;
  durationMillis: number | null;
  running: boolean;
  comments: ApprovalComment[];
}

/** 承認フローの図を描くための情報。 */
export interface ProcessDiagram {
  /** プロセス定義の BPMN 2.0 XML（図形情報つき） */
  bpmnXml: string;
  /** 実行中のアクティビティID */
  currentActivityIds: string[];
  /** 通過済みのアクティビティID */
  completedActivityIds: string[];
  /** 通過済みのシーケンスフローID（どちらへ分岐したかが分かる） */
  takenFlowIds: string[];
}

/** ログイン中のユーザー。画面に出す操作を決めるのに使う。 */
export interface CurrentUser {
  userId: string;
  authorities: string[];
  canManageProcessDefinitions: boolean;
}

/** 配備済みのプロセス定義1版。 */
export interface ProcessDefinitionVersion {
  processDefinitionId: string;
  key: string;
  name: string;
  version: number;
  deploymentId: string;
  deploymentName: string | null;
  resourceName: string;
  deployedAt: string | null;
  /** 停止中の版では新規に起票できない */
  suspended: boolean;
  /** 最新版。新規の起票はこの版で始まる */
  latest: boolean;
  /** この版で走っている申請の件数 */
  runningInstanceCount: number;
}

export interface ReminderTriggerResult {
  processInstanceId: string;
  firedTimers: number;
}

/** サーバの GlobalExceptionHandler が返す RFC 7807 風のエラー。 */
export interface ErrorResponse {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string;
}

export interface Credentials {
  username: string;
  password: string;
}

/** 画面下部に出す API 呼び出しの記録。 */
export interface ApiLogEntry {
  id: number;
  at: string;
  method: string;
  path: string;
  status: number;
  ok: boolean;
  requestBody: unknown;
  responseBody: unknown;
}

export interface ExpenseRequestInput {
  title: string;
  amount: number;
  expenseDate: string;
  category: string;
  remarks: string | null;
}
