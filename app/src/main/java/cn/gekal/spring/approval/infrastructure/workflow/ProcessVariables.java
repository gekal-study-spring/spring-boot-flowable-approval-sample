package cn.gekal.spring.approval.infrastructure.workflow;

/** BPMN 定義と共有するプロセス変数名・定義ID。BPMN 側の文字列と食い違うと実行時まで気付けないため、ここに集約する。 */
public final class ProcessVariables {

  /** プロセス定義キー。 */
  public static final String PROCESS_DEFINITION_KEY = "expenseApprovalProcess";

  /** 課長承認タスクの定義ID。 */
  public static final String TASK_MANAGER_APPROVAL = "userTaskManagerApproval";

  /** 部長承認タスクの定義ID。 */
  public static final String TASK_DIRECTOR_APPROVAL = "userTaskDirectorApproval";

  public static final String APPLICANT_ID = "applicantId";
  public static final String TITLE = "title";
  public static final String AMOUNT = "amount";
  public static final String EXPENSE_DATE = "expenseDate";
  public static final String CATEGORY = "category";
  public static final String REMARKS = "remarks";
  public static final String APPROVED = "approved";
  public static final String APPROVAL_COMMENT = "approvalComment";
  public static final String APPROVER_ID = "approverId";
  public static final String ERP_VOUCHER_NO = "erpVoucherNo";
  public static final String REMINDER_COUNT = "reminderCount";

  private ProcessVariables() {}
}
