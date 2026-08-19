package cn.gekal.spring.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gekal.spring.approval.domain.model.ExpenseAmount;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.InvalidExpenseRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 経費精算申請の不変条件のテスト。Spring を起動せず素の JUnit で検証する。 */
class ExpenseRequestTest {

  @Test
  @DisplayName("正しい入力なら申請を作成でき、プロセスインスタンスIDは未確定である")
  void create() {
    ExpenseRequest request =
        ExpenseRequest.create("yamada", "出張旅費", 50_000L, LocalDate.now(), "旅費交通費", "9月出張分");

    assertThat(request.processInstanceId()).isNull();
    assertThat(request.amount()).isEqualTo(ExpenseAmount.of(50_000L));
    assertThat(request.applicantId()).isEqualTo("yamada");
  }

  @Test
  @DisplayName("金額が0以下なら作成できない")
  void rejectNonPositiveAmount() {
    assertThatThrownBy(
            () -> ExpenseRequest.create("yamada", "出張旅費", 0L, LocalDate.now(), "旅費交通費", null))
        .isInstanceOf(InvalidExpenseRequestException.class)
        .hasMessageContaining("1円以上");
  }

  @Test
  @DisplayName("上限を超える金額は作成できない")
  void rejectTooLargeAmount() {
    assertThatThrownBy(
            () ->
                ExpenseRequest.create(
                    "yamada", "設備購入", ExpenseAmount.MAX_YEN + 1, LocalDate.now(), "備品費", null))
        .isInstanceOf(InvalidExpenseRequestException.class)
        .hasMessageContaining("上限");
  }

  @Test
  @DisplayName("未来日の支出は作成できない")
  void rejectFutureExpenseDate() {
    assertThatThrownBy(
            () ->
                ExpenseRequest.create(
                    "yamada", "出張旅費", 1_000L, LocalDate.now().plusDays(1), "旅費交通費", null))
        .isInstanceOf(InvalidExpenseRequestException.class)
        .hasMessageContaining("未来日");
  }

  @Test
  @DisplayName("件名・費目・申請者IDは必須である")
  void rejectMissingRequiredFields() {
    assertThatThrownBy(
            () -> ExpenseRequest.create("yamada", " ", 1_000L, LocalDate.now(), "旅費交通費", null))
        .isInstanceOf(InvalidExpenseRequestException.class);
    assertThatThrownBy(
            () -> ExpenseRequest.create("yamada", "出張旅費", 1_000L, LocalDate.now(), " ", null))
        .isInstanceOf(InvalidExpenseRequestException.class);
    assertThatThrownBy(
            () -> ExpenseRequest.create(null, "出張旅費", 1_000L, LocalDate.now(), "旅費交通費", null))
        .isInstanceOf(InvalidExpenseRequestException.class);
  }

  @Test
  @DisplayName("プロセスインスタンスIDを確定させても元の申請内容は変わらない")
  void withProcessInstanceId() {
    ExpenseRequest request =
        ExpenseRequest.create("yamada", "出張旅費", 50_000L, LocalDate.now(), "旅費交通費", null);
    ExpenseRequest started = request.withProcessInstanceId("proc-1");

    assertThat(started.processInstanceId()).isEqualTo("proc-1");
    assertThat(request.processInstanceId()).isNull();
    assertThat(started.title()).isEqualTo(request.title());
  }
}
