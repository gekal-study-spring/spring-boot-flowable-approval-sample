import type { ProcessDefinitionVersion } from './api-types';

/**
 * フロー定義1件ぶんのまとめ。
 *
 * 管理画面は版を新しい順に並べた配列を受け取るが、プロセスが複数あると版が混ざって読みにくい。
 * プロセスごとにまとめ直し、一覧では最新版の状態だけを見せて、履歴は選んだものだけ出す。
 */
export interface ProcessDefinitionSummary {
  key: string;
  name: string;
  /** 新規の起票に使われる版。 */
  latest: ProcessDefinitionVersion;
  /** 新しい順の全版。 */
  versions: ProcessDefinitionVersion[];
  /** 全版を合わせた走行中の件数。 */
  runningInstanceCount: number;
}

/**
 * 版の配列をプロセスごとにまとめる。
 *
 * 版は新しい順に並べ直す。`latest` はサーバが付けた印を優先し、無い場合（停止などで印が
 * 付かない場合）は版数が最大のものを使う。
 */
export function groupProcessDefinitions(
  versions: ProcessDefinitionVersion[]
): ProcessDefinitionSummary[] {
  const byKey = new Map<string, ProcessDefinitionVersion[]>();
  versions.forEach(version => {
    const list = byKey.get(version.key);
    if (list === undefined) {
      byKey.set(version.key, [version]);
    } else {
      list.push(version);
    }
  });

  return [...byKey.entries()]
    .map(([key, list]) => {
      const sorted = [...list].sort((a, b) => b.version - a.version);
      const latest = sorted.find(version => version.latest) ?? sorted[0];
      return {
        key,
        name: latest.name,
        latest,
        versions: sorted,
        runningInstanceCount: sorted.reduce(
          (total, version) => total + version.runningInstanceCount,
          0
        ),
      };
    })
    .sort((a, b) => a.key.localeCompare(b.key));
}
