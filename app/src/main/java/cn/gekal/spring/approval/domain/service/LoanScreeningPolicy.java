package cn.gekal.spring.approval.domain.service;

import cn.gekal.spring.approval.domain.model.CreditDecision;

/**
 * 個人ローンの審査規程。
 *
 * <p>「どのスコアなら自動で通すか」「いくらから部長決裁か」は金額やスコア単体では決められない規程なので、ドメインサービスとして切り出している。BPMN の決裁区分の分岐も {@code
 * ${loanScreeningPolicy.requiresExecutiveApproval(requestedAmountYen)}} でこのクラスを呼ぶ。
 *
 * <p>実運用ではこの判定を DMN の決定表へ移すと、審査基準をアプリの再デプロイなしに差し替えられる。
 */
public class LoanScreeningPolicy {

  /** 部長決裁が必要になる金額の下限（円）。 */
  public static final long EXECUTIVE_APPROVAL_THRESHOLD_YEN = 5_000_000L;

  /** 自動承認の上限額（円）。これを超えると必ず人手審査へ回す。 */
  public static final long AUTO_APPROVE_MAX_AMOUNT_YEN = 1_000_000L;

  /** 自動承認に必要なスコアの下限。 */
  public static final int AUTO_APPROVE_MIN_SCORE = 700;

  /** これを下回ると自動で謝絶するスコア。 */
  public static final int AUTO_DECLINE_MAX_SCORE = 400;

  /** 年収に対する借入額の上限倍率。これを超えると人手審査へ回す。 */
  public static final double INCOME_MULTIPLE_LIMIT = 3.0;

  /** 部長決裁が必要か。BPMN の決裁区分ゲートウェイから呼ばれる。 */
  public boolean requiresExecutiveApproval(long requestedAmountYen) {
    return requestedAmountYen >= EXECUTIVE_APPROVAL_THRESHOLD_YEN;
  }

  /**
   * 照会結果と申込内容から判定区分を決める。
   *
   * <p>謝絶の条件を先に見る。制裁リストに当たった場合はスコアによらず謝絶する。
   */
  public CreditDecision decide(
      int creditScore, boolean sanctionHit, long requestedAmountYen, long annualIncomeYen) {
    if (sanctionHit || creditScore <= AUTO_DECLINE_MAX_SCORE) {
      return CreditDecision.AUTO_DECLINE;
    }
    if (exceedsIncomeLimit(requestedAmountYen, annualIncomeYen)) {
      return CreditDecision.MANUAL;
    }
    if (creditScore >= AUTO_APPROVE_MIN_SCORE
        && requestedAmountYen <= AUTO_APPROVE_MAX_AMOUNT_YEN) {
      return CreditDecision.AUTO_APPROVE;
    }
    return CreditDecision.MANUAL;
  }

  /** 年収倍率の上限を超えているか。年収が不明（0以下）なら超過扱いにして人手へ回す。 */
  public boolean exceedsIncomeLimit(long requestedAmountYen, long annualIncomeYen) {
    if (annualIncomeYen <= 0) {
      return true;
    }
    return requestedAmountYen > annualIncomeYen * INCOME_MULTIPLE_LIMIT;
  }
}
