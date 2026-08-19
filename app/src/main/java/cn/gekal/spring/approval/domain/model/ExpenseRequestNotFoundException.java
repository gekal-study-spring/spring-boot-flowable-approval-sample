package cn.gekal.spring.approval.domain.model;

/** 指定された経費精算申請が存在しないことを表す例外。 */
public class ExpenseRequestNotFoundException extends RuntimeException {

  public ExpenseRequestNotFoundException(String processInstanceId) {
    super("経費精算申請が見つかりません: " + processInstanceId);
  }
}
