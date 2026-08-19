package cn.gekal.spring.approval.domain.model;

/** 経費精算申請の不変条件に違反したことを表す例外。 */
public class InvalidExpenseRequestException extends RuntimeException {

  public InvalidExpenseRequestException(String message) {
    super(message);
  }
}
