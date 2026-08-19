import { API_BASE } from '@/config/site';
import type {
  ApiLogEntry,
  ApprovalHistoryEntry,
  ApprovalTask,
  Credentials,
  ErrorResponse,
  ExpenseRequest,
  ExpenseRequestInput,
  ProcessDiagram,
  ReminderTriggerResult,
} from './api-types';

/** API 呼び出しが失敗したことを表す例外。サーバのエラーレスポンスをそのまま保持する。 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ErrorResponse | string | null
  ) {
    super(typeof body === 'object' && body !== null ? body.detail : `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

let logSequence = 0;
type LogListener = (entry: ApiLogEntry) => void;
const listeners = new Set<LogListener>();

/** API ログの購読。ログパネルが使う。戻り値を呼ぶと購読を解除する。 */
export function onApiLog(listener: LogListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function basicAuthHeader({ username, password }: Credentials): string {
  // 資格情報に非 ASCII が入っても壊れないよう、UTF-8 で符号化してから base64 にする
  const encoded = new TextEncoder().encode(`${username}:${password}`);
  return `Basic ${btoa(String.fromCharCode(...encoded))}`;
}

async function request<T>(
  credentials: Credentials,
  method: string,
  path: string,
  body?: unknown
): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      Authorization: basicAuthHeader(credentials),
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  let parsed: unknown = null;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  logSequence += 1;
  const entry: ApiLogEntry = {
    id: logSequence,
    at: new Date().toLocaleTimeString('ja-JP'),
    method,
    path,
    status: response.status,
    ok: response.ok,
    requestBody: body ?? null,
    responseBody: parsed,
  };
  listeners.forEach(listener => listener(entry));

  if (!response.ok) {
    throw new ApiError(response.status, parsed as ErrorResponse | string | null);
  }
  return parsed as T;
}

export const api = {
  /** 認証確認を兼ねた申請一覧の取得。 */
  login: (credentials: Credentials) =>
    request<ExpenseRequest[]>(credentials, 'GET', '/api/expense-requests'),

  myRequests: (credentials: Credentials) =>
    request<ExpenseRequest[]>(credentials, 'GET', '/api/expense-requests'),

  createRequest: (credentials: Credentials, payload: ExpenseRequestInput) =>
    request<ExpenseRequest>(credentials, 'POST', '/api/expense-requests', payload),

  history: (credentials: Credentials, processInstanceId: string) =>
    request<ApprovalHistoryEntry[]>(
      credentials,
      'GET',
      `/api/expense-requests/${processInstanceId}/history`
    ),

  diagram: (credentials: Credentials, processInstanceId: string) =>
    request<ProcessDiagram>(
      credentials,
      'GET',
      `/api/expense-requests/${processInstanceId}/diagram`
    ),

  myTasks: (credentials: Credentials) => request<ApprovalTask[]>(credentials, 'GET', '/api/tasks'),

  approve: (credentials: Credentials, taskId: string, comment: string) =>
    request<ExpenseRequest>(credentials, 'POST', `/api/tasks/${taskId}/approve`, { comment }),

  reject: (credentials: Credentials, taskId: string, comment: string) =>
    request<ExpenseRequest>(credentials, 'POST', `/api/tasks/${taskId}/reject`, { comment }),

  fireReminders: (credentials: Credentials, processInstanceId: string) =>
    request<ReminderTriggerResult>(credentials, 'POST', `/api/demo/reminders/${processInstanceId}`),
};
