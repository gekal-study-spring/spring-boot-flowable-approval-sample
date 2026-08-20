'use client';

import CenterFocusStrongIcon from '@mui/icons-material/CenterFocusStrong';
import CloseIcon from '@mui/icons-material/Close';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import Alert from '@mui/material/Alert';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ProcessDiagram } from '@/lib/api-types';

/** 通常表示時の図の高さ。 */
const DIAGRAM_HEIGHT = 420;

/** 図の外周に空ける余白。全体表示したときに端が切れないようにする。 */
const FIT_MARGIN = 20;

/** bpmn-visualization から使う操作だけを型として書き出す。 */
interface BpmnRenderer {
  load: (xml: string, options?: { fit?: { type: string; margin?: number } }) => void;
  bpmnElementsRegistry: {
    addCssClasses: (ids: string | string[], classNames: string | string[]) => void;
  };
  dispose: () => void;
}

/** ボタンとキーボードから呼ぶ図の操作。描画のたびに作り直す。 */
interface DiagramControls {
  zoomIn: () => void;
  zoomOut: () => void;
  fit: () => void;
}

/**
 * 図をパンするために触る内部グラフ。
 *
 * bpmn-visualization の公開 API には移動の手段が無く（`fit` と `zoom` だけ）、
 * ライブラリ自身がズームで使っている `view.scaleAndTranslate` を同じように使う。
 */
interface BpmnGraph {
  container: HTMLElement;
  getGraphBounds: () => { x: number; y: number; width: number; height: number };
  view: {
    scale: number;
    translate: { x: number; y: number };
    scaleAndTranslate: (scale: number, dx: number, dy: number) => void;
  };
}

/** ホイールの移動量を px に揃える。行単位で送ってくるマウスがあるため。 */
const LINE_HEIGHT_PX = 16;

/**
 * 図を動かしても画面に残す最小の量（px）。
 *
 * 動かす範囲を無制限にすると図を画面外へ追い出して見失う。端まで来たら動かさないので、
 * そこから先のスワイプはページのスクロールに渡る（戻れなくなる心配はない）。
 */
const KEEP_VISIBLE_PX = 48;

function toPixels(delta: number, deltaMode: number): number {
  return deltaMode === WheelEvent.DOM_DELTA_LINE ? delta * LINE_HEIGHT_PX : delta;
}

/**
 * 1軸ぶんの移動量を、図が画面に残る範囲に収める。
 *
 * 図が入れ物に収まっていても動かせるようにしている（全体表示の直後でも2本指スワイプが効く）。
 * 端に達すると 0 を返すので、呼び出し側はそこからページのスクロールへ委譲できる。
 */
function clampPan(position: number, size: number, viewport: number, movement: number): number {
  const min = KEEP_VISIBLE_PX - size;
  const max = viewport - KEEP_VISIBLE_PX;
  const next = Math.min(max, Math.max(min, position + movement));
  return next - position;
}

/**
 * 2本指スワイプで図を動かす。動かせたら true。
 *
 * 端まで来た方向へは動かさず false を返すので、呼び出し側はページのスクロールに委ねられる
 * （macOS で入れ子のスクロール領域が端で親に引き継ぐのと同じ挙動）。
 */
function panBy(graph: BpmnGraph, deltaX: number, deltaY: number): boolean {
  const bounds = graph.getGraphBounds();
  const appliedX = clampPan(bounds.x, bounds.width, graph.container.clientWidth, -deltaX);
  const appliedY = clampPan(bounds.y, bounds.height, graph.container.clientHeight, -deltaY);
  if (appliedX === 0 && appliedY === 0) {
    return false;
  }
  const { view } = graph;
  view.scaleAndTranslate(
    view.scale,
    view.translate.x + appliedX / view.scale,
    view.translate.y + appliedY / view.scale
  );
  return true;
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
 * 通過済み・実行中の色の凡例。
 *
 * 定義そのものを見るとき（進捗の色付けがないとき）は色の説明が意味を持たないため、凡例だけ省く。
 */
function Legend({ showProgress }: { showProgress: boolean }) {
  if (!showProgress) {
    return null;
  }
  return (
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
    </Stack>
  );
}

/** 拡大・縮小・全体表示のボタン。ホイールを使わずに操作できるようにする。 */
function ZoomControls({ controls }: { controls: DiagramControls | null }) {
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
      <Tooltip title="拡大">
        <span>
          <IconButton size="small" disabled={controls === null} onClick={() => controls?.zoomIn()}>
            <ZoomInIcon fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
      <Tooltip title="縮小">
        <span>
          <IconButton size="small" disabled={controls === null} onClick={() => controls?.zoomOut()}>
            <ZoomOutIcon fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
      <Tooltip title="全体表示に戻す">
        <span>
          <IconButton size="small" disabled={controls === null} onClick={() => controls?.fit()}>
            <CenterFocusStrongIcon fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
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
  const [controls, setControls] = useState<DiagramControls | null>(null);
  // パンのたびに触るため、再描画を起こさない ref で持つ
  const graphRef = useRef<BpmnGraph | null>(null);

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
      const { BpmnVisualization, FitType, ZoomType } = await import('bpmn-visualization');
      if (cancelled) {
        return;
      }
      const instance = new BpmnVisualization({
        container,
        navigation: { enabled: true },
      });
      renderer = instance as unknown as BpmnRenderer;
      try {
        instance.load(diagram.bpmnXml, { fit: { type: FitType.Center, margin: FIT_MARGIN } });
        addCssClassesSafely(renderer, diagram.completedActivityIds, 'bpmn-completed');
        addCssClassesSafely(renderer, diagram.takenFlowIds, 'bpmn-taken');
        // 実行中は最後に塗って、通過済みの色より優先させる
        addCssClassesSafely(renderer, diagram.currentActivityIds, 'bpmn-current');
        graphRef.current = instance.graph as unknown as BpmnGraph;
        setControls({
          zoomIn: () => instance.navigation.zoom(ZoomType.In),
          zoomOut: () => instance.navigation.zoom(ZoomType.Out),
          fit: () => instance.navigation.fit({ type: FitType.Center, margin: FIT_MARGIN }),
        });
        setError(null);
      } catch (e) {
        setError(`フロー図を描画できませんでした: ${String(e)}`);
      }
    };

    void render();

    return () => {
      cancelled = true;
      graphRef.current = null;
      setControls(null);
      renderer?.dispose();
    };
  }, [diagram, container]);

  /**
   * トラックパッドの操作に合わせたホイールの扱い。
   *
   * macOS では2本指スワイプが移動、ピンチが拡大縮小で、ブラウザは前者を素のホイール、
   * 後者を `Ctrl + ホイール` として送ってくる。それぞれをそのまま図の移動・拡大縮小に対応させる。
   *
   * 端まで動かしきったときは `preventDefault` せずにページのスクロールへ委ねる。入れ子の
   * スクロール領域が端で親に引き継ぐ macOS の挙動に揃えているので、図の上でも下へ読み進められる。
   */
  useEffect(() => {
    if (container === null) {
      return;
    }
    const handleWheel = (event: WheelEvent) => {
      // ピンチはライブラリの拡大縮小に任せる（判定条件をライブラリ側と合わせてある）
      if (event.ctrlKey && !event.altKey && !event.shiftKey && !event.metaKey) {
        return;
      }
      const graph = graphRef.current;
      // ライブラリは素のホイールを扱わないので、ここで止めて自前で移動させる
      event.stopPropagation();
      if (graph === null) {
        return;
      }
      const panned = panBy(
        graph,
        toPixels(event.deltaX, event.deltaMode),
        toPixels(event.deltaY, event.deltaMode)
      );
      if (panned) {
        event.preventDefault();
      }
    };
    // passive: false を明示しないと preventDefault が効かないブラウザがある
    container.addEventListener('wheel', handleWheel, { capture: true, passive: false });
    return () => container.removeEventListener('wheel', handleWheel, { capture: true });
  }, [container]);

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
      tabIndex={0}
      role="img"
      aria-label="承認フロー図。フォーカス中は + と - で拡大縮小、0 で全体表示に戻せます"
      onKeyDown={event => {
        // キーボードだけでも操作できるようにする
        if (event.key === '+' || event.key === ';' || event.key === '=') {
          controls?.zoomIn();
        } else if (event.key === '-') {
          controls?.zoomOut();
        } else if (event.key === '0') {
          controls?.fit();
        } else {
          return;
        }
        event.preventDefault();
      }}
      sx={{
        height: fullscreen ? '100%' : DIAGRAM_HEIGHT,
        flexGrow: fullscreen ? 1 : 0,
        border: fullscreen ? 'none' : '1px solid',
        borderColor: 'divider',
        borderRadius: fullscreen ? 0 : 1,
        bgcolor: 'background.paper',
        overflow: 'hidden',
        cursor: 'grab',
        '&:active': { cursor: 'grabbing' },
        '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main' },
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
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Legend showProgress={showProgress} />
          <Typography variant="caption" color="text.secondary">
            2本指スワイプ・ドラッグで移動、ピンチで拡大縮小
          </Typography>
        </Stack>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <ZoomControls controls={controls} />
          <Divider orientation="vertical" flexItem />
          <Button
            size="small"
            variant="outlined"
            startIcon={<FullscreenIcon />}
            onClick={() => setFullscreen(true)}
          >
            全画面
          </Button>
        </Stack>
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
            <Typography variant="subtitle2">承認フロー図</Typography>
            <Legend showProgress={showProgress} />
            <Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>
              2本指スワイプ・ドラッグで移動、ピンチで拡大縮小
            </Typography>
            <ZoomControls controls={controls} />
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
