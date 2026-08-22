import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { ProcessDefinitionVersion } from './api-types';
import { groupProcessDefinitions } from './process-definitions';

function version(key: string, no: number, latest: boolean, running = 0): ProcessDefinitionVersion {
  return {
    processDefinitionId: `${key}:${no}:x`,
    key,
    name: `${key} の表示名`,
    version: no,
    deploymentId: `d-${no}`,
    deploymentName: 'PackagedBootstrap',
    resourceName: `${key}.bpmn20.xml`,
    deployedAt: '2026-08-21T00:00:00',
    suspended: false,
    latest,
    runningInstanceCount: running,
  };
}

test('プロセスごとにまとめ、キー順に並べる', () => {
  const grouped = groupProcessDefinitions([
    version('loanScreeningProcess', 1, true),
    version('expenseApprovalProcess', 2, true),
    version('expenseApprovalProcess', 1, false),
  ]);

  assert.deepEqual(
    grouped.map(summary => summary.key),
    ['expenseApprovalProcess', 'loanScreeningProcess']
  );
  assert.equal(grouped[0].versions.length, 2);
  assert.equal(grouped[1].versions.length, 1);
});

test('版は新しい順に並び、最新版が latest になる', () => {
  const grouped = groupProcessDefinitions([
    version('p', 1, false),
    version('p', 3, true),
    version('p', 2, false),
  ]);

  assert.deepEqual(
    grouped[0].versions.map(v => v.version),
    [3, 2, 1]
  );
  assert.equal(grouped[0].latest.version, 3);
});

test('走行中の件数は全版の合計になる', () => {
  const grouped = groupProcessDefinitions([version('p', 2, true, 1), version('p', 1, false, 5)]);
  assert.equal(grouped[0].runningInstanceCount, 6);
});

// 全版が停止しているなど、サーバ側で latest の印が付かない場合の保険
test('latest の印が無ければ版数が最大のものを最新として扱う', () => {
  const grouped = groupProcessDefinitions([version('p', 1, false), version('p', 4, false)]);
  assert.equal(grouped[0].latest.version, 4);
});

test('空の配列なら空を返す', () => {
  assert.deepEqual(groupProcessDefinitions([]), []);
});
