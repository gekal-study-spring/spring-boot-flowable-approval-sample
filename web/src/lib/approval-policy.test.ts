import assert from 'node:assert/strict';
import { test } from 'node:test';
import { approvalRouteLabel, parseAmountYen, requiresDirectorApproval } from './approval-policy';

test('10万円未満は課長承認へ回る', () => {
  assert.equal(requiresDirectorApproval(99_999), false);
  assert.equal(approvalRouteLabel(99_999), '課長承認');
});

test('10万円ちょうどから部長承認になる', () => {
  assert.equal(requiresDirectorApproval(100_000), true);
  assert.equal(approvalRouteLabel(100_000), '部長承認');
});

test('数字の文字列を円に変換する', () => {
  assert.equal(parseAmountYen('50000'), 50000);
  assert.equal(parseAmountYen('1'), 1);
});

// 打ち直しのために欄を空にしたとき、0 が書き戻されて入力できなくなる不具合があった
test('金額が空欄のあいだは null を返す（0 に落とさない）', () => {
  assert.equal(parseAmountYen(''), null);
  assert.equal(parseAmountYen('   '), null);
});

test('0・負数・小数・数値でない文字列は金額として受け付けない', () => {
  assert.equal(parseAmountYen('0'), null);
  assert.equal(parseAmountYen('-100'), null);
  assert.equal(parseAmountYen('1.5'), null);
  assert.equal(parseAmountYen('abc'), null);
});
