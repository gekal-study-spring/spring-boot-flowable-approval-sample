'use client';

import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useState } from 'react';
import { LoginForm } from '@/components/molecules/login-form';
import { RequestForm } from '@/components/molecules/request-form';
import { ApiLogPanel } from '@/components/organisms/api-log-panel';
import { ApprovalHistory } from '@/components/organisms/approval-history';
import { RequestDetail } from '@/components/organisms/request-detail';
import { RequestTable } from '@/components/organisms/request-table';
import { TaskList } from '@/components/organisms/task-list';
import { SAMPLE_USERS, SITE } from '@/config/site';
import { ApiError, api, onApiLog } from '@/lib/api-client';
import type {
  ApiLogEntry,
  ApprovalHistoryEntry,
  ApprovalTask,
  Credentials,
  ExpenseRequest,
} from '@/lib/api-types';

const MAX_LOG_ENTRIES = 30;
const AUTO_REFRESH_INTERVAL_MS = 3000;

interface Message {
  text: string;
  severity: 'success' | 'error';
}

/** 見出し付きのカード。画面の各区画をそろえるための入れ物。 */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle2" sx={{ mb: 1.5 }}>
          {title}
        </Typography>
        {children}
      </CardContent>
    </Card>
  );
}

/** 承認プロセスの動作確認コンソール本体。状態はこの部品に集約する。 */
export function ApprovalConsole() {
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [requests, setRequests] = useState<ExpenseRequest[]>([]);
  const [tasks, setTasks] = useState<ApprovalTask[]>([]);
  const [history, setHistory] = useState<ApprovalHistoryEntry[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState<Message | null>(null);
  const [logs, setLogs] = useState<ApiLogEntry[]>([]);
  const [autoRefresh, setAutoRefresh] = useState(true);

  useEffect(
    () => onApiLog(entry => setLogs(current => [entry, ...current].slice(0, MAX_LOG_ENTRIES))),
    []
  );

  /** エラーをメッセージ表示に変換する。認証切れならログイン画面へ戻す。 */
  const handleError = useCallback((error: unknown) => {
    if (error instanceof ApiError) {
      if (error.status === 401) {
        setCredentials(null);
        setMessage({
          text: '認証に失敗しました。ユーザー名とパスワードを確認してください。',
          severity: 'error',
        });
        return;
      }
      const detail =
        typeof error.body === 'object' && error.body !== null ? error.body.detail : error.message;
      setMessage({ text: `エラー (${error.status}): ${detail}`, severity: 'error' });
      return;
    }
    setMessage({ text: `通信に失敗しました: ${String(error)}`, severity: 'error' });
  }, []);

  const reload = useCallback(
    async (current: Credentials, processInstanceId: string | null) => {
      try {
        const [loadedRequests, loadedTasks] = await Promise.all([
          api.myRequests(current),
          api.myTasks(current),
        ]);
        setRequests(loadedRequests);
        setTasks(loadedTasks);
        // 履歴は選択中の申請のぶんだけ引く
        setHistory(processInstanceId === null ? [] : await api.history(current, processInstanceId));
      } catch (error) {
        handleError(error);
      }
    },
    [handleError]
  );

  // 非同期 Service Task やタイマーの結果は遅れて反映されるため、既定で定期的に読み直す
  useEffect(() => {
    if (credentials === null || !autoRefresh) {
      return;
    }
    const timer = setInterval(() => void reload(credentials, selectedId), AUTO_REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [credentials, autoRefresh, reload, selectedId]);

  const login = async (entered: Credentials) => {
    try {
      await api.login(entered);
      setCredentials(entered);
      setMessage({ text: `${entered.username} でログインしました。`, severity: 'success' });
      await reload(entered, null);
    } catch (error) {
      handleError(error);
    }
  };

  const loggedInUser = SAMPLE_USERS.find(user => user.username === credentials?.username);
  const selected = requests.find(request => request.processInstanceId === selectedId) ?? null;

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="h6">{SITE.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {SITE.description}
          </Typography>
        </Box>

        <Card variant="outlined">
          <CardContent>
            {credentials === null ? (
              <LoginForm onLogin={login} />
            ) : (
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                sx={{ alignItems: { sm: 'center' } }}
              >
                <Chip size="small" color="primary" label="ログイン中" />
                <Typography sx={{ fontWeight: 600 }}>
                  {loggedInUser ? loggedInUser.label : credentials.username}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  権限: {loggedInUser ? loggedInUser.groups : '不明'}
                </Typography>
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{ alignItems: 'center', ml: { sm: 'auto' } }}
                >
                  <FormControlLabel
                    control={
                      <Switch
                        size="small"
                        checked={autoRefresh}
                        onChange={event => setAutoRefresh(event.target.checked)}
                      />
                    }
                    label="自動更新（3秒）"
                  />
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => void reload(credentials, selectedId)}
                  >
                    再読み込み
                  </Button>
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => {
                      setCredentials(null);
                      setRequests([]);
                      setTasks([]);
                      setSelectedId(null);
                      setMessage({ text: 'ログアウトしました。', severity: 'success' });
                    }}
                  >
                    ログアウト
                  </Button>
                </Stack>
              </Stack>
            )}
          </CardContent>
        </Card>

        {message !== null && (
          <Alert severity={message.severity} onClose={() => setMessage(null)}>
            {message.text}
          </Alert>
        )}

        {credentials === null ? (
          <Section title="ログインしてください">
            <Typography variant="body2" color="text.secondary" component="div">
              ユーザーごとに見える画面が変わります。
              <ul>
                <li>山田（申請者）: 申請の起票と自分の申請の確認</li>
                <li>佐藤（課長）: 10万円未満の申請の承認・却下</li>
                <li>田中（部長）: 10万円以上の申請の承認・却下</li>
              </ul>
            </Typography>
          </Section>
        ) : (
          <>
            <Section title="経費精算を申請する">
              <RequestForm
                onSubmit={async input => {
                  try {
                    const created = await api.createRequest(credentials, input);
                    setSelectedId(created.processInstanceId);
                    setMessage({
                      text: `申請しました。承認待ちタスク: ${created.currentTaskName ?? '-'}`,
                      severity: 'success',
                    });
                    await reload(credentials, created.processInstanceId);
                  } catch (error) {
                    handleError(error);
                  }
                }}
              />
            </Section>

            <Section title={`承認タスク（${tasks.length} 件）`}>
              <TaskList
                tasks={tasks}
                onDecide={async (task, decision, comment) => {
                  try {
                    const result =
                      decision === 'approve'
                        ? await api.approve(credentials, task.taskId, comment)
                        : await api.reject(credentials, task.taskId, comment);
                    setSelectedId(result.processInstanceId);
                    setMessage({
                      text:
                        decision === 'approve'
                          ? '承認しました。基幹システム連携は非同期のため、数秒後に伝票番号が入ります。'
                          : '却下しました。申請者へ通知されます。',
                      severity: 'success',
                    });
                    await reload(credentials, result.processInstanceId);
                  } catch (error) {
                    handleError(error);
                  }
                }}
              />
            </Section>

            <Section title={`自分の申請（${requests.length} 件）`}>
              <RequestTable
                requests={requests}
                selectedId={selectedId}
                onSelect={request => {
                  setSelectedId(request.processInstanceId);
                  void reload(credentials, request.processInstanceId);
                }}
                onFireReminders={async request => {
                  try {
                    const result = await api.fireReminders(credentials, request.processInstanceId);
                    setMessage({
                      text: `リマインドタイマーを ${result.firedTimers} 件期限切れにしました。数秒後にリマインド回数が増えます。`,
                      severity: 'success',
                    });
                  } catch (error) {
                    handleError(error);
                  }
                }}
              />
            </Section>

            <Section title="申請の詳細（プロセス変数）">
              <RequestDetail request={selected} />
            </Section>

            <Section title="承認履歴">
              <ApprovalHistory entries={history} />
            </Section>
          </>
        )}

        <Section title="API ログ">
          <ApiLogPanel entries={logs} onClear={() => setLogs([])} />
        </Section>
      </Stack>
    </Container>
  );
}
