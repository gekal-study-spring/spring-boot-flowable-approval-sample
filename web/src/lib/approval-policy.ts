/**
 * 承認ルーティング規程（画面表示用）。
 *
 * 実際の振り分けはサーバ側の ExpenseApprovalPolicy が行い、BPMN の分岐条件もそれを呼ぶ。
 * ここは「申請前にどちらへ回るか」を表示するためだけの写しなので、閾値を変えるときは
 * サーバ側（app/src/main/java/.../domain/service/ExpenseApprovalPolicy.java）と合わせること。
 */
export const DIRECTOR_APPROVAL_THRESHOLD_YEN = 100_000;

/** 部長承認が必要か。 */
export function requiresDirectorApproval(amountYen: number): boolean {
  return amountYen >= DIRECTOR_APPROVAL_THRESHOLD_YEN;
}

/** 申請金額から回付先の承認タスク名を返す。 */
export function approvalRouteLabel(amountYen: number): string {
  return requiresDirectorApproval(amountYen) ? '部長承認' : '課長承認';
}

/**
 * 入力中の金額文字列を円に変換する。空欄や数値でない間は null を返す。
 *
 * 金額を number で持つと、打ち直しのために欄を空にした瞬間に `Number('')` が 0 になり、
 * 0 が欄へ書き戻されて入力できなくなる。入力中は文字列のまま持ち、確定時にだけ数値にする。
 */
export function parseAmountYen(input: string): number | null {
  if (input.trim() === '') {
    return null;
  }
  const value = Number(input);
  return Number.isInteger(value) && value > 0 ? value : null;
}
