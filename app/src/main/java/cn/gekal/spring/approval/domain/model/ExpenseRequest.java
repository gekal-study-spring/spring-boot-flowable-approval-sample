package cn.gekal.spring.approval.domain.model;

import java.time.LocalDate;

/**
 * 経費精算申請。
 *
 * <p>申請内容の不変条件はこのクラスが守る。プロセスインスタンスIDはワークフロー起動後に採番されるため、起票時点では未確定（{@code null}）である。
 */
public class ExpenseRequest {

  private static final int TITLE_MAX_LENGTH = 100;
  private static final int REMARKS_MAX_LENGTH = 500;

  private final String processInstanceId;
  private final String applicantId;
  private final String title;
  private final ExpenseAmount amount;
  private final LocalDate expenseDate;
  private final String category;
  private final String remarks;

  private ExpenseRequest(
      String processInstanceId,
      String applicantId,
      String title,
      ExpenseAmount amount,
      LocalDate expenseDate,
      String category,
      String remarks) {
    this.processInstanceId = processInstanceId;
    this.applicantId = applicantId;
    this.title = title;
    this.amount = amount;
    this.expenseDate = expenseDate;
    this.category = category;
    this.remarks = remarks;
  }

  /** 新規起票。ワークフロー起動前なのでプロセスインスタンスIDは持たない。 */
  public static ExpenseRequest create(
      String applicantId,
      String title,
      long amountYen,
      LocalDate expenseDate,
      String category,
      String remarks) {
    validateApplicantId(applicantId);
    validateTitle(title);
    validateExpenseDate(expenseDate);
    validateCategory(category);
    validateRemarks(remarks);
    return new ExpenseRequest(
        null, applicantId, title, ExpenseAmount.of(amountYen), expenseDate, category, remarks);
  }

  /** 永続化（プロセス変数）からの再構築。検証済みの前提で組み立てる。 */
  public static ExpenseRequest reconstruct(
      String processInstanceId,
      String applicantId,
      String title,
      long amountYen,
      LocalDate expenseDate,
      String category,
      String remarks) {
    return new ExpenseRequest(
        processInstanceId,
        applicantId,
        title,
        ExpenseAmount.of(amountYen),
        expenseDate,
        category,
        remarks);
  }

  /** ワークフロー起動によりプロセスインスタンスIDが確定した申請を返す。 */
  public ExpenseRequest withProcessInstanceId(String newProcessInstanceId) {
    if (newProcessInstanceId == null || newProcessInstanceId.isBlank()) {
      throw new InvalidExpenseRequestException("プロセスインスタンスIDが空です");
    }
    return new ExpenseRequest(
        newProcessInstanceId, applicantId, title, amount, expenseDate, category, remarks);
  }

  private static void validateApplicantId(String applicantId) {
    if (applicantId == null || applicantId.isBlank()) {
      throw new InvalidExpenseRequestException("申請者IDは必須です");
    }
  }

  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new InvalidExpenseRequestException("件名は必須です");
    }
    if (title.length() > TITLE_MAX_LENGTH) {
      throw new InvalidExpenseRequestException("件名は" + TITLE_MAX_LENGTH + "文字以内で指定してください");
    }
  }

  private static void validateExpenseDate(LocalDate expenseDate) {
    if (expenseDate == null) {
      throw new InvalidExpenseRequestException("支出日は必須です");
    }
    // 未来日の経費は精算対象にならない（まだ支出が発生していないため）
    if (expenseDate.isAfter(LocalDate.now())) {
      throw new InvalidExpenseRequestException("支出日に未来日は指定できません: " + expenseDate);
    }
  }

  private static void validateCategory(String category) {
    if (category == null || category.isBlank()) {
      throw new InvalidExpenseRequestException("費目は必須です");
    }
  }

  private static void validateRemarks(String remarks) {
    if (remarks != null && remarks.length() > REMARKS_MAX_LENGTH) {
      throw new InvalidExpenseRequestException("備考は" + REMARKS_MAX_LENGTH + "文字以内で指定してください");
    }
  }

  public String processInstanceId() {
    return processInstanceId;
  }

  public String applicantId() {
    return applicantId;
  }

  public String title() {
    return title;
  }

  public ExpenseAmount amount() {
    return amount;
  }

  public LocalDate expenseDate() {
    return expenseDate;
  }

  public String category() {
    return category;
  }

  public String remarks() {
    return remarks;
  }
}
