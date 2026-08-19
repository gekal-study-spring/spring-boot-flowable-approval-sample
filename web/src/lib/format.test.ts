import assert from 'node:assert/strict';
import { test } from 'node:test';
import { formatDateTime, formatDuration, formatYen } from './format';

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

test('所要時間は単位に応じて丸める', () => {
  assert.equal(formatDuration(57060), '57.1 秒');
  assert.equal(formatDuration(192000), '3 分 12 秒');
  assert.equal(formatDuration(3_900_000), '1 時間 5 分');
  assert.equal(formatDuration(90_000_000), '1 日 1 時間');
});

test('所要時間が未確定ならハイフンを表示する', () => {
  assert.equal(formatDuration(null), '-');
});
