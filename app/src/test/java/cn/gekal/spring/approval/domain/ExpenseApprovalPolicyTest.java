package cn.gekal.spring.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseAmount;
import cn.gekal.spring.approval.domain.service.ExpenseApprovalPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 承認ルーティング規程のテスト。BPMN の分岐条件もこのクラスを呼ぶため、境界値はここで担保する。 */
class ExpenseApprovalPolicyTest {

  private final ExpenseApprovalPolicy policy = new ExpenseApprovalPolicy();

  @ParameterizedTest
  @CsvSource({"1,false", "99999,false", "100000,true", "100001,true"})
  @DisplayName("10万円以上なら部長承認が必要になる")
  void requiresDirectorApproval(long amountYen, boolean expected) {
    assertThat(policy.requiresDirectorApproval(amountYen)).isEqualTo(expected);
  }

  @Test
  @DisplayName("金額から承認者ロールが決まる")
  void approverRoleOf() {
    assertThat(policy.approverRoleOf(ExpenseAmount.of(99_999L))).isEqualTo(ApproverRole.MANAGER);
    assertThat(policy.approverRoleOf(ExpenseAmount.of(100_000L))).isEqualTo(ApproverRole.DIRECTOR);
  }
}
