package cn.gekal.spring.approval.domain.model;

/** 与信スコアリングの判定区分。BPMN の分岐条件が文字列としてこの名前を見る。 */
public enum CreditDecision {
  /** スコアが低い、または照合に当たったため自動で謝絶する。 */
  AUTO_DECLINE,
  /** 少額かつ高スコアのため、人手を介さず契約へ進む。 */
  AUTO_APPROVE,
  /** 人手による審査へ回す。 */
  MANUAL
}
