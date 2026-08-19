package cn.gekal.spring.approval.domain.model;

/** 承認者の判断。 */
public enum ApprovalDecision {
  /** 承認。基幹システム連携へ進む。 */
  APPROVED(true),
  /** 却下。申請者へ通知して終了する。 */
  REJECTED(false);

  private final boolean approved;

  ApprovalDecision(boolean approved) {
    this.approved = approved;
  }

  /** BPMN の判定用プロセス変数 {@code approved} に渡す値。 */
  public boolean isApproved() {
    return approved;
  }
}
