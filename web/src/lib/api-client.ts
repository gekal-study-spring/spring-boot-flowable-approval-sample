import { API_BASE } from '@/config/site';
import type {
  ApiLogEntry,
  ApprovalHistoryEntry,
  ApprovalTask,
  Credentials,
  CurrentUser,
  ErrorResponse,
  ExpenseRequest,
  ExpenseRequestInput,
  ProcessDefinitionVersion,
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

/**
 * API を呼び、結果をログへ流す。
 *
 * body に FormData を渡すとファイルアップロードになる。境界文字列はブラウザに決めさせる必要があるため、
 * その場合は Content-Type を付けない（付けると boundary が落ちてサーバが解釈できない）。
 */
async function request<T>(
  credentials: Credentials,
  method: string,
  path: string,
  body?: unknown,
  accept: string = 'application/json'
): Promise<T> {
  const isFormData = body instanceof FormData;
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      Authorization: basicAuthHeader(credentials),
      Accept: accept,
      ...(body === undefined || isFormData ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
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
    requestBody: body instanceof FormData ? '(multipart/form-data)' : (body ?? null),
    responseBody: parsed,
  };
  listeners.forEach(listener => listener(entry));

  if (!response.ok) {
    throw new ApiError(response.status, parsed as ErrorResponse | string | null);
  }
  return parsed as T;
}

export const api = {
  /** 認証確認を兼ねた、ログイン中のユーザーの取得。権限もここで分かる。 */
  login: (credentials: Credentials) => request<CurrentUser>(credentials, 'GET', '/api/me'),

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

  processDefinitions: (credentials: Credentials) =>
    request<ProcessDefinitionVersion[]>(credentials, 'GET', '/api/admin/process-definitions'),

  deployProcessDefinition: (credentials: Credentials, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ProcessDefinitionVersion>(
      credentials,
      'POST',
      '/api/admin/process-definitions',
      form
    );
  },

  processDefinitionBpmn: (credentials: Credentials, processDefinitionId: string) =>
    request<string>(
      credentials,
      'GET',
      `/api/admin/process-definitions/${processDefinitionId}/bpmn`,
      undefined,
      'application/xml'
    ),

  rollbackProcessDefinition: (credentials: Credentials, processDefinitionId: string) =>
    request<ProcessDefinitionVersion>(
      credentials,
      'POST',
      `/api/admin/process-definitions/${processDefinitionId}/rollback`
    ),

  suspendProcessDefinition: (credentials: Credentials, processDefinitionId: string) =>
    request<void>(
      credentials,
      'POST',
      `/api/admin/process-definitions/${processDefinitionId}/suspend`
    ),

  activateProcessDefinition: (credentials: Credentials, processDefinitionId: string) =>
    request<void>(
      credentials,
      'POST',
      `/api/admin/process-definitions/${processDefinitionId}/activate`
    ),
};
