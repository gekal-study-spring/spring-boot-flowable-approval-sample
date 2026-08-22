'use client';

import UploadFileIcon from '@mui/icons-material/UploadFile';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useMemo, useRef, useState } from 'react';
import type { ProcessDefinitionVersion } from '@/lib/api-types';
import { formatDateTime } from '@/lib/format';
import { groupProcessDefinitions } from '@/lib/process-definitions';

interface Props {
  versions: ProcessDefinitionVersion[];
  busy: boolean;
  onDeploy: (file: File) => void;
  onRollback: (version: ProcessDefinitionVersion) => void;
  onToggleSuspension: (version: ProcessDefinitionVersion) => void;
  onShowDiagram: (version: ProcessDefinitionVersion) => void;
}

/** 版の状態を表す印。一覧と履歴で同じ見た目にそろえる。 */
function VersionState({ version }: { version: ProcessDefinitionVersion }) {
  if (version.suspended) {
    return <Chip size="small" color="warning" label="停止中" />;
  }
  if (version.latest) {
    return <Chip size="small" color="primary" label="最新" />;
  }
  return <Chip size="small" variant="outlined" label="旧版" />;
}

/**
 * 承認フロー定義の運用パネル。
 *
 * プロセスが複数あると版が混ざって読めないため、上段で**フロー定義を1行ずつ**見せ、
 * 選んだ定義の**配備履歴**だけを下段に出す。
 */
export function ProcessDefinitionPanel({
  versions,
  busy,
  onDeploy,
  onRollback,
  onToggleSuspension,
  onShowDiagram,
}: Props) {
  const fileInput = useRef<HTMLInputElement | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const definitions = useMemo(() => groupProcessDefinitions(versions), [versions]);
  // 配備し直しても選択が外れないよう、キーで引き直す。無ければ先頭を見せる
  const selected =
    definitions.find(definition => definition.key === selectedKey) ?? definitions[0] ?? null;

  return (
    <Stack spacing={2}>
      <Alert severity="info" variant="outlined">
        配備した時点から<strong>新規の起票だけ</strong>
        が新しい版で始まります。走行中の申請は起票時の版のまま完了するため、
        アプリの再起動は要りません。ただし BPMN が新しい delegate や candidateGroups
        を参照する場合は、 アプリのデプロイも必要です。
      </Alert>

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { sm: 'center' } }}
      >
        <input
          ref={fileInput}
          type="file"
          accept=".bpmn20.xml,.bpmn,application/xml,text/xml"
          hidden
          onChange={event => {
            const file = event.target.files?.[0];
            if (file !== undefined) {
              setFileName(file.name);
              onDeploy(file);
            }
            // 同じファイルを選び直しても change が起きるようにする
            event.target.value = '';
          }}
        />
        <Button
          variant="contained"
          size="small"
          startIcon={<UploadFileIcon />}
          disabled={busy}
          onClick={() => fileInput.current?.click()}
        >
          BPMN を配備する
        </Button>
        <Typography variant="body2" color="text.secondary">
          {fileName === null
            ? 'ファイル名は .bpmn20.xml か .bpmn で終わる必要があります'
            : `直近に選んだファイル: ${fileName}`}
        </Typography>
      </Stack>

      {/* ── 上段: フロー定義の一覧 ── */}
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>
          フロー定義（{definitions.length} 件）
        </Typography>
        {definitions.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            配備された定義がありません。
          </Typography>
        ) : (
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>プロセス</TableCell>
                  <TableCell>定義キー</TableCell>
                  <TableCell align="right">最新版</TableCell>
                  <TableCell>状態</TableCell>
                  <TableCell align="right">走行中</TableCell>
                  <TableCell align="right">版数</TableCell>
                  <TableCell>最終配備</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {definitions.map(definition => (
                  <TableRow
                    key={definition.key}
                    hover
                    selected={selected?.key === definition.key}
                    sx={{ cursor: 'pointer' }}
                    onClick={() => setSelectedKey(definition.key)}
                  >
                    <TableCell sx={{ fontWeight: 600 }}>{definition.name}</TableCell>
                    <TableCell>
                      <Typography variant="caption">{definition.key}</Typography>
                    </TableCell>
                    <TableCell align="right">v{definition.latest.version}</TableCell>
                    <TableCell>
                      <VersionState version={definition.latest} />
                    </TableCell>
                    <TableCell align="right">{definition.runningInstanceCount}</TableCell>
                    <TableCell align="right">{definition.versions.length}</TableCell>
                    <TableCell>{formatDateTime(definition.latest.deployedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Box>

      {/* ── 下段: 選択した定義の配備履歴 ── */}
      {selected !== null && (
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            配備履歴 — {selected.name}（{selected.versions.length} 版）
          </Typography>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell align="right">版</TableCell>
                  <TableCell>状態</TableCell>
                  <TableCell>配備の由来</TableCell>
                  <TableCell>配備日時</TableCell>
                  <TableCell align="right">走行中</TableCell>
                  <TableCell>操作</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {selected.versions.map(version => (
                  <TableRow key={version.processDefinitionId} hover>
                    <TableCell align="right">
                      <Typography variant="body2" sx={{ fontWeight: version.latest ? 700 : 400 }}>
                        v{version.version}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <VersionState version={version} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>
                      <Typography
                        variant="caption"
                        noWrap
                        title={version.deploymentName ?? ''}
                        sx={{ display: 'block' }}
                      >
                        {version.deploymentName ?? '-'}
                      </Typography>
                    </TableCell>
                    <TableCell>{formatDateTime(version.deployedAt)}</TableCell>
                    <TableCell align="right">{version.runningInstanceCount}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5}>
                        <Button size="small" onClick={() => onShowDiagram(version)} disabled={busy}>
                          図を見る
                        </Button>
                        <Button
                          size="small"
                          onClick={() => onRollback(version)}
                          disabled={busy || version.latest}
                        >
                          この版に戻す
                        </Button>
                        <Button
                          size="small"
                          color={version.suspended ? 'success' : 'warning'}
                          onClick={() => onToggleSuspension(version)}
                          disabled={busy}
                        >
                          {version.suspended ? '再開' : '停止'}
                        </Button>
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}

      <Typography variant="caption" color="text.secondary">
        「この版に戻す」は古い版を復活させるのではなく、その内容で新しい版を配備します （Flowable
        は版を消さないため、戻した事実も履歴に残ります）。
        「停止」はその版での新規の起票だけを止め、走行中の申請には影響しません。
      </Typography>
    </Stack>
  );
}
