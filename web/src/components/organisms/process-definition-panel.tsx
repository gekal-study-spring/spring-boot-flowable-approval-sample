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
import { useRef, useState } from 'react';
import type { ProcessDefinitionVersion } from '@/lib/api-types';
import { formatDateTime } from '@/lib/format';

interface Props {
  versions: ProcessDefinitionVersion[];
  busy: boolean;
  onDeploy: (file: File) => void;
  onRollback: (version: ProcessDefinitionVersion) => void;
  onToggleSuspension: (version: ProcessDefinitionVersion) => void;
  onShowDiagram: (version: ProcessDefinitionVersion) => void;
}

/**
 * 承認フロー定義の運用パネル。
 *
 * BPMN を配備すると、その時点から新規の起票が新しい版で始まる（アプリの再起動は要らない）。
 * 走行中の申請は起票時の版のまま完了するため、「走行中」の件数が古い版をいつ片付けられるかの目安になる。
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

  return (
    <Stack spacing={1.5}>
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

      {versions.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          配備された定義がありません。
        </Typography>
      ) : (
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
              {versions.map(version => (
                <TableRow key={version.processDefinitionId} hover>
                  <TableCell align="right">
                    <Typography variant="body2" sx={{ fontWeight: version.latest ? 700 : 400 }}>
                      v{version.version}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5}>
                      {version.latest && <Chip size="small" color="primary" label="最新" />}
                      {version.suspended && <Chip size="small" color="warning" label="停止中" />}
                      {!version.latest && !version.suspended && (
                        <Chip size="small" variant="outlined" label="旧版" />
                      )}
                    </Stack>
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
      )}

      <Box>
        <Typography variant="caption" color="text.secondary">
          「この版に戻す」は古い版を復活させるのではなく、その内容で新しい版を配備します （Flowable
          は版を消さないため、戻した事実も履歴に残ります）。
          「停止」はその版での新規の起票だけを止め、走行中の申請には影響しません。
        </Typography>
      </Box>
    </Stack>
  );
}
