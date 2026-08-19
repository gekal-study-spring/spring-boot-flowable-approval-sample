import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatDateTime, formatYen } from './format';

test('金額は桁区切りされる', () => {
  assert.equal(formatYen(50000), '50,000 円');
  assert.equal(formatYen(0), '0 円');
});

test('日時は秒までを空白区切りで表示する', () => {
  assert.equal(formatDateTime('2026-08-19T21:19:24.497'), '2026-08-19 21:19:24');
});

test('日時が未設定ならハイフンを表示する', () => {
  assert.equal(formatDateTime(null), '-');
});
