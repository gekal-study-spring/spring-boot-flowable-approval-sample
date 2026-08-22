'use client';

import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useState } from 'react';
import { LoginForm } from '@/components/molecules/login-form';
import { RequestForm } from '@/components/molecules/request-form';
import { ApiLogPanel } from '@/components/organisms/api-log-panel';
import { ApprovalHistory } from '@/components/organisms/approval-history';
import { ProcessDefinitionPanel } from '@/components/organisms/process-definition-panel';
import { ProcessDiagramView } from '@/components/organisms/process-diagram';
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
  CurrentUser,
  ExpenseRequest,
  ProcessDefinitionVersion,
  ProcessDiagram,
} from '@/lib/api-types';

const MAX_LOG_ENTRIES = 30;

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
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [requests, setRequests] = useState<ExpenseRequest[]>([]);
  const [tasks, setTasks] = useState<ApprovalTask[]>([]);
  const [history, setHistory] = useState<ApprovalHistoryEntry[]>([]);
  const [diagram, setDiagram] = useState<ProcessDiagram | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState<Message | null>(null);
  const [logs, setLogs] = useState<ApiLogEntry[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshedAt, setRefreshedAt] = useState<string | null>(null);
  const [definitions, setDefinitions] = useState<ProcessDefinitionVersion[]>([]);
  const [definitionDiagram, setDefinitionDiagram] = useState<ProcessDiagram | null>(null);
  const [adminBusy, setAdminBusy] = useState(false);

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
    async (current: Credentials, processInstanceId: string | null, user: CurrentUser | null) => {
      try {
        const [loadedRequests, loadedTasks] = await Promise.all([
          api.myRequests(current),
          api.myTasks(current),
        ]);
        setRequests(loadedRequests);
        setTasks(loadedTasks);
        // フロー定義はその権限を持つ人だけが読める。持たない人に 403 を出しても意味がない
        if (user?.canManageProcessDefinitions === true) {
          setDefinitions(await api.processDefinitions(current));
        }
        // 履歴とフロー図は選択中の申請のぶんだけ引く
        if (processInstanceId === null) {
          setHistory([]);
          setDiagram(null);
        } else {
          const [loadedHistory, loadedDiagram] = await Promise.all([
            api.history(current, processInstanceId),
            api.diagram(current, processInstanceId),
          ]);
          setHistory(loadedHistory);
          setDiagram(loadedDiagram);
        }
        setRefreshedAt(new Date().toLocaleTimeString('ja-JP'));
      } catch (error) {
        handleError(error);
      } finally {
        setRefreshing(false);
      }
    },
    [handleError]
  );

  /** 画面の内容を読み直す。基幹システム連携やリマインドは非同期なので、結果はこの操作で取りに行く。 */
  const refresh = useCallback(
    (current: Credentials, processInstanceId: string | null) => {
      setRefreshing(true);
      void reload(current, processInstanceId, currentUser);
    },
    [reload, currentUser]
  );

  const login = async (entered: Credentials) => {
    try {
      const user = await api.login(entered);
      setCredentials(entered);
      setCurrentUser(user);
      setMessage({ text: `${entered.username} でログインしました。`, severity: 'success' });
      await reload(entered, null, user);
    } catch (error) {
      handleError(error);
    }
  };

  /** フロー定義を操作したあと、一覧を読み直して結果を反映する。 */
  const runAdminAction = async (action: () => Promise<string>) => {
    if (credentials === null) {
      return;
    }
    setAdminBusy(true);
    try {
      setMessage({ text: await action(), severity: 'success' });
      setDefinitions(await api.processDefinitions(credentials));
    } catch (error) {
      handleError(error);
    } finally {
      setAdminBusy(false);
    }
  };

  const loggedInUser = SAMPLE_USERS.find(user => user.username === credentials?.username);
  const canApply = currentUser?.authorities.includes('applicants') === true;
  const canApprove =
    currentUser?.authorities.some(
      authority => authority === 'managers' || authority === 'directors'
    ) === true;
  const canManageDefinitions = currentUser?.canManageProcessDefinitions === true;
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
                  権限: {currentUser === null ? '取得中' : currentUser.authorities.join(', ')}
                </Typography>
                <Stack
                  direction="row"
                  spacing={1}
                  sx={{ alignItems: 'center', ml: { sm: 'auto' } }}
                >
                  <Typography variant="body2" color="text.secondary">
                    {refreshedAt === null ? '未取得' : `最終更新 ${refreshedAt}`}
                  </Typography>
                  <Button
                    size="small"
                    variant="contained"
                    disabled={refreshing}
                    onClick={() => refresh(credentials, selectedId)}
                  >
                    {refreshing ? '更新中…' : '再読み込み'}
                  </Button>
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => {
                      setCredentials(null);
                      setCurrentUser(null);
                      setRequests([]);
                      setTasks([]);
                      setDefinitions([]);
                      setDefinitionDiagram(null);
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
                <li>admin（運用者）: 承認フロー定義の差し替え・切り戻し</li>
              </ul>
            </Typography>
          </Section>
        ) : (
          <>
            {canApply && (
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
                      await reload(credentials, created.processInstanceId, currentUser);
                    } catch (error) {
                      handleError(error);
                    }
                  }}
                />
              </Section>
            )}

            {canApprove && (
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
                            ? '承認しました。基幹システム連携は非同期のため、少し待ってから「再読み込み」すると伝票番号が入ります。'
                            : '却下しました。申請者へ通知されます。',
                        severity: 'success',
                      });
                      await reload(credentials, result.processInstanceId, currentUser);
                    } catch (error) {
                      handleError(error);
                    }
                  }}
                />
              </Section>
            )}

            {canApply && (
              <Section title={`自分の申請（${requests.length} 件）`}>
                <RequestTable
                  requests={requests}
                  selectedId={selectedId}
                  onSelect={request => {
                    setSelectedId(request.processInstanceId);
                    refresh(credentials, request.processInstanceId);
                  }}
                  onFireReminders={async request => {
                    try {
                      const result = await api.fireReminders(
                        credentials,
                        request.processInstanceId
                      );
                      setMessage({
                        text: `リマインドタイマーを ${result.firedTimers} 件期限切れにしました。「再読み込み」でリマインド回数が増えます。`,
                        severity: 'success',
                      });
                    } catch (error) {
                      handleError(error);
                    }
                  }}
                />
              </Section>
            )}

            {canApply && (
              <>
                <Section title="申請の詳細（プロセス変数）">
                  <RequestDetail request={selected} />
                </Section>

                <Section title="承認フロー図">
                  <ProcessDiagramView diagram={diagram} />
                </Section>

                <Section title="承認履歴">
                  <ApprovalHistory entries={history} />
                </Section>
              </>
            )}

            {canManageDefinitions && (
              <Section title="承認フロー定義">
                <Stack spacing={2}>
                  <ProcessDefinitionPanel
                    versions={definitions}
                    busy={adminBusy}
                    onDeploy={file =>
                      void runAdminAction(async () => {
                        const deployed = await api.deployProcessDefinition(credentials, file);
                        setDefinitionDiagram(null);
                        return `v${deployed.version} を配備しました。以後の起票はこの版で始まります（再起動は不要）。`;
                      })
                    }
                    onRollback={version =>
                      void runAdminAction(async () => {
                        const deployed = await api.rollbackProcessDefinition(
                          credentials,
                          version.processDefinitionId
                        );
                        setDefinitionDiagram(null);
                        return `v${version.version} の内容を v${deployed.version} として配備しました。`;
                      })
                    }
                    onToggleSuspension={version =>
                      void runAdminAction(async () => {
                        if (version.suspended) {
                          await api.activateProcessDefinition(
                            credentials,
                            version.processDefinitionId
                          );
                          return `v${version.version} を再開しました。`;
                        }
                        await api.suspendProcessDefinition(
                          credentials,
                          version.processDefinitionId
                        );
                        return `v${version.version} を停止しました。走行中の申請は影響を受けません。`;
                      })
                    }
                    onShowDiagram={version =>
                      void runAdminAction(async () => {
                        const bpmnXml = await api.processDefinitionBpmn(
                          credentials,
                          version.processDefinitionId
                        );
                        // 定義そのものを見るだけなので、通過済み・実行中の色付けはしない
                        setDefinitionDiagram({
                          bpmnXml,
                          currentActivityIds: [],
                          completedActivityIds: [],
                          takenFlowIds: [],
                        });
                        return `v${version.version} の BPMN を表示しました。`;
                      })
                    }
                  />
                  {definitionDiagram !== null && <ProcessDiagramView diagram={definitionDiagram} />}
                </Stack>
              </Section>
            )}
          </>
        )}

        <Section title="API ログ">
          <ApiLogPanel entries={logs} onClear={() => setLogs([])} />
        </Section>
      </Stack>
    </Container>
  );
}
