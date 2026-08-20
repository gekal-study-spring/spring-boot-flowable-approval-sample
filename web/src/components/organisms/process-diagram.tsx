'use client';

import CloseIcon from '@mui/icons-material/Close';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import Alert from '@mui/material/Alert';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useState } from 'react';
import type { ProcessDiagram } from '@/lib/api-types';

/** 通常表示時の図の高さ。 */
const DIAGRAM_HEIGHT = 420;

/** bpmn-visualization から使う操作だけを型として書き出す。 */
interface BpmnRenderer {
  load: (xml: string, options?: { fit?: { type: string; margin?: number } }) => void;
  bpmnElementsRegistry: {
    addCssClasses: (ids: string | string[], classNames: string | string[]) => void;
  };
  dispose: () => void;
}

/**
 * 図に無い要素IDが来ても描画全体を落とさないようにする。
 *
 * BPMN を差し替えた直後などに、履歴側のIDが新しい図に存在しないことがある。
 */
function addCssClassesSafely(renderer: BpmnRenderer, ids: string[], className: string): void {
  ids.forEach(id => {
    try {
      renderer.bpmnElementsRegistry.addCssClasses(id, className);
    } catch {
      // 図に無い要素は塗らなくてよい
    }
  });
}

/**
 * 通過済み・実行中の色の凡例。通常表示と全画面表示の両方で使う。
 *
 * 定義そのものを見るとき（進捗の色付けがないとき）は色の説明が意味を持たないため、凡例だけ省く。
 */
function Legend({ showProgress }: { showProgress: boolean }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
      {showProgress && (
        <>
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
        </>
      )}
      <Typography variant="caption" color="text.secondary">
        ドラッグで移動、ホイールで拡大縮小できます
      </Typography>
    </Stack>
  );
}

/**
 * 承認フローの BPMN 図。
 *
 * サーバから受け取った BPMN 定義をそのまま描き、通過済み・実行中の要素に色を付ける。
 * 画像をサーバで生成しないので、日本語ラベルもブラウザのフォントで綺麗に出る。
 *
 * 描画は bpmn-visualization（Apache-2.0）で行う。bpmn-js はライセンスで bpmn.io の
 * ウォーターマークの除去・隠蔽を禁じているため、ロゴを出さずに使えるこちらを選んでいる。
 *
 * 全画面表示は Dialog で行う。図の描画先が入れ替わるため、切り替えのたびに描き直す。
 */
export function ProcessDiagramView({
  diagram,
  showProgress = true,
}: {
  diagram: ProcessDiagram | null;
  /** 通過済み・実行中の凡例を出すか。定義を見るだけの用途では false にする */
  showProgress?: boolean;
}) {
  // 全画面の切り替えで描画先の要素が入れ替わる。ref だと差し替わった瞬間を取りこぼすため、
  // コールバック ref で state に持ち、要素が変わるたびに描き直す
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fullscreen, setFullscreen] = useState(false);

  const attachContainer = useCallback((node: HTMLDivElement | null) => {
    setContainer(node);
  }, []);

  useEffect(() => {
    if (diagram === null || container === null) {
      return;
    }

    let renderer: BpmnRenderer | null = null;
    let cancelled = false;

    const render = async () => {
      // DOM を触るライブラリなので、クライアントで動くこの時点で読み込む
      const { BpmnVisualization, FitType } = await import('bpmn-visualization');
      if (cancelled) {
        return;
      }
      const instance = new BpmnVisualization({
        container,
        navigation: { enabled: true },
      }) as unknown as BpmnRenderer;
      renderer = instance;
      try {
        instance.load(diagram.bpmnXml, { fit: { type: FitType.Center, margin: 20 } });
        addCssClassesSafely(instance, diagram.completedActivityIds, 'bpmn-completed');
        addCssClassesSafely(instance, diagram.takenFlowIds, 'bpmn-taken');
        // 実行中は最後に塗って、通過済みの色より優先させる
        addCssClassesSafely(instance, diagram.currentActivityIds, 'bpmn-current');
        setError(null);
      } catch (e) {
        setError(`フロー図を描画できませんでした: ${String(e)}`);
      }
    };

    void render();

    return () => {
      cancelled = true;
      renderer?.dispose();
    };
  }, [diagram, container]);

  if (diagram === null) {
    return (
      <Typography variant="body2" color="text.secondary">
        一覧の行を選ぶと、その申請がフローのどこまで進んだかを図で表示します。
      </Typography>
    );
  }

  const canvas = (
    <Box
      ref={attachContainer}
      sx={{
        height: fullscreen ? '100%' : DIAGRAM_HEIGHT,
        flexGrow: fullscreen ? 1 : 0,
        border: fullscreen ? 'none' : '1px solid',
        borderColor: 'divider',
        borderRadius: fullscreen ? 0 : 1,
        bgcolor: 'background.paper',
        overflow: 'hidden',
      }}
    />
  );

  return (
    <Stack spacing={1}>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between' }}
      >
        <Legend showProgress={showProgress} />
        <Button
          size="small"
          variant="outlined"
          startIcon={<FullscreenIcon />}
          onClick={() => setFullscreen(true)}
        >
          全画面
        </Button>
      </Stack>
      {error !== null && <Alert severity="error">{error}</Alert>}
      {!fullscreen && canvas}

      <Dialog
        fullScreen
        open={fullscreen}
        onClose={() => setFullscreen(false)}
        // 表示の途中で寸法を測ると図が収まらないため、アニメーションなしで開く
        transitionDuration={0}
      >
        <AppBar position="static" color="default" elevation={0}>
          <Toolbar variant="dense" sx={{ gap: 2 }}>
            <Typography variant="subtitle2" sx={{ flexGrow: 1 }}>
              承認フロー図
            </Typography>
            <Legend showProgress={showProgress} />
            <IconButton edge="end" aria-label="全画面を終了" onClick={() => setFullscreen(false)}>
              <CloseIcon />
            </IconButton>
          </Toolbar>
        </AppBar>
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
          {fullscreen && canvas}
        </Box>
      </Dialog>
    </Stack>
  );
}
