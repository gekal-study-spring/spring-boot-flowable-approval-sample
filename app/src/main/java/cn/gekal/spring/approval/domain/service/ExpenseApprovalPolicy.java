package cn.gekal.spring.approval.domain.service;

import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseAmount;

/**
 * 経費精算の承認ルーティング規程。
 *
 * <p>「いくらから部長承認か」は金額オブジェクト単体では決められない規程なので、ドメインサービスとして切り出している。BPMN の分岐条件も {@code
 * ${expenseApprovalPolicy.requiresDirectorApproval(amount)}} でこのクラスを呼び、閾値の定義をここ1箇所に閉じ込める。
 */
public class ExpenseApprovalPolicy {

  /** 部長承認が必要になる金額の下限（円）。 */
  public static final long DIRECTOR_APPROVAL_THRESHOLD_YEN = 100_000L;

  /** BPMN の Exclusive Gateway から呼ばれる判定。プロセス変数 {@code amount} をそのまま受ける。 */
  public boolean requiresDirectorApproval(long amountYen) {
    return amountYen >= DIRECTOR_APPROVAL_THRESHOLD_YEN;
  }

  /** 金額から承認者ロールを決める。 */
  public ApproverRole approverRoleOf(ExpenseAmount amount) {
    return amount.isAtLeast(DIRECTOR_APPROVAL_THRESHOLD_YEN)
        ? ApproverRole.DIRECTOR
        : ApproverRole.MANAGER;
  }
}
