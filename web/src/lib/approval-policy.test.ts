import assert from 'node:assert/strict';
import { test } from 'node:test';
import { approvalRouteLabel, requiresDirectorApproval } from './approval-policy';

test('10万円未満は課長承認へ回る', () => {
  assert.equal(requiresDirectorApproval(99_999), false);
  assert.equal(approvalRouteLabel(99_999), '課長承認');
});

test('10万円ちょうどから部長承認になる', () => {
  assert.equal(requiresDirectorApproval(100_000), true);
  assert.equal(approvalRouteLabel(100_000), '部長承認');
});
