package cn.gekal.spring.approval.infrastructure.workflow;

/** 個人ローン審査の BPMN と共有するプロセス変数名・定義ID。食い違うと実行時まで気付けないため、ここに集約する。 */
public final class LoanProcessVariables {

  /** プロセス定義キー。 */
  public static final String PROCESS_DEFINITION_KEY = "loanScreeningProcess";

  public static final String TASK_DOCUMENT_CHECK = "userTaskDocumentCheck";
  public static final String TASK_ADDITIONAL_DOCUMENTS = "userTaskAdditionalDocuments";
  public static final String TASK_ANALYST_REVIEW = "userTaskAnalystReview";
  public static final String TASK_MANAGER_APPROVAL = "userTaskManagerApproval";
  public static final String TASK_EXECUTIVE_APPROVAL = "userTaskExecutiveApproval";
  public static final String TASK_CONTRACT_SIGNING = "userTaskContractSigning";

  /** 申込内容。実運用では業務テーブルへ移し、ここには applicationId だけを残す想定。 */
  public static final String APPLICANT_ID = "applicantId";

  public static final String PRODUCT_TYPE = "productType";
  public static final String REQUESTED_AMOUNT_YEN = "requestedAmountYen";
  public static final String PURPOSE = "purpose";
  public static final String ANNUAL_INCOME_YEN = "annualIncomeYen";

  /** 書類確認の結果。false のあいだは差戻しを繰り返す。 */
  public static final String DOCUMENTS_COMPLETE = "documentsComplete";

  public static final String DOCUMENT_NOTE = "documentNote";

  /** 外部照会の結果。 */
  public static final String CREDIT_SCORE = "creditScore";

  public static final String BUREAU_STATUS = "bureauStatus";
  public static final String SANCTION_HIT = "sanctionHit";

  /** 与信判定の区分。{@link cn.gekal.spring.approval.domain.model.CreditDecision} の名前が入る。 */
  public static final String CREDIT_DECISION = "creditDecision";

  /** 人手審査の結果。多段のため、各段の所見は履歴（コメント）側に残す。 */
  public static final String APPROVED = "approved";

  public static final String APPROVAL_COMMENT = "approvalComment";
  public static final String APPROVER_ID = "approverId";

  /** 契約・実行。 */
  public static final String CONTRACT_NO = "contractNo";

  public static final String DECLINE_REASON = "declineReason";

  private LoanProcessVariables() {}
}
