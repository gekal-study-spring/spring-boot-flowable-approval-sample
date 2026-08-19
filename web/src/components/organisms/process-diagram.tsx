'use client';

import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import type { ProcessDiagram } from '@/lib/api-types';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

/** bpmn-js の canvas から使う操作だけを型として書き出す（ライブラリの型は緩いため）。 */
interface BpmnCanvas {
  zoom: (mode: string, center?: string) => void;
  addMarker: (elementId: string, marker: string) => void;
}

interface BpmnViewer {
  importXML: (xml: string) => Promise<{ warnings: unknown[] }>;
  get: (name: string) => unknown;
  destroy: () => void;
}

/** 図に無い要素IDが来ても描画全体を落とさないようにする。 */
function addMarkerSafely(canvas: BpmnCanvas, elementId: string, marker: string): void {
  try {
    canvas.addMarker(elementId, marker);
  } catch {
    // BPMN を差し替えた直後などに履歴側のIDが図に存在しないことがある。無視してよい
  }
}

/**
 * 承認フローの BPMN 図。
 *
 * サーバから受け取った BPMN 定義をそのまま描き、通過済み・実行中の要素に色を付ける。
 * 画像をサーバで生成しないので、日本語ラベルもブラウザのフォントで綺麗に出る。
 */
export function ProcessDiagramView({ diagram }: { diagram: ProcessDiagram | null }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (diagram === null || container === null) {
      return;
    }

    let viewer: BpmnViewer | null = null;
    let cancelled = false;

    const render = async () => {
      // bpmn-js は DOM を触るため、クライアントで動くこの時点で読み込む
      const { default: NavigatedViewer } = await import('bpmn-js/lib/NavigatedViewer');
      if (cancelled) {
        return;
      }
      const instance = new NavigatedViewer({ container }) as unknown as BpmnViewer;
      viewer = instance;
      try {
        await instance.importXML(diagram.bpmnXml);
        if (cancelled) {
          return;
        }
        const canvas = instance.get('canvas') as BpmnCanvas;
        canvas.zoom('fit-viewport', 'auto');
        diagram.completedActivityIds.forEach(id => addMarkerSafely(canvas, id, 'bpmn-completed'));
        diagram.takenFlowIds.forEach(id => addMarkerSafely(canvas, id, 'bpmn-taken'));
        // 実行中は最後に塗って、通過済みの色より優先させる
        diagram.currentActivityIds.forEach(id => addMarkerSafely(canvas, id, 'bpmn-current'));
        setError(null);
      } catch (e) {
        setError(`フロー図を描画できませんでした: ${String(e)}`);
      }
    };

    void render();

    return () => {
      cancelled = true;
      viewer?.destroy();
    };
  }, [diagram]);

  if (diagram === null) {
    return (
      <Typography variant="body2" color="text.secondary">
        一覧の行を選ぶと、その申請がフローのどこまで進んだかを図で表示します。
      </Typography>
    );
  }

  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip
          size="small"
          label="通過済み"
          sx={{ bgcolor: '#e7f5ec', border: '1px solid #197b3f', color: '#197b3f' }}
        />
        <Chip
          size="small"
          label="実行中"
          sx={{ bgcolor: '#fff4e0', border: '2px solid #b26a00', color: '#b26a00' }}
        />
        <Typography variant="caption" color="text.secondary">
          ドラッグで移動、ホイールで拡大縮小できます
        </Typography>
      </Stack>
      {error !== null && <Alert severity="error">{error}</Alert>}
      <Box
        ref={containerRef}
        sx={{
          height: 420,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          bgcolor: 'background.paper',
          overflow: 'hidden',
        }}
      />
    </Stack>
  );
}
