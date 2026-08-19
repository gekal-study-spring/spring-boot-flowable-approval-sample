package cn.gekal.spring.approval.domain.model;

/** 経費精算申請の進行状況。 */
public enum ExpenseRequestStatus {
  /** 承認待ち（プロセス実行中）。 */
  IN_PROGRESS,
  /** 承認され、基幹システムへ連携済み。 */
  APPROVED,
  /** 却下され、申請者へ通知済み。 */
  REJECTED
}
