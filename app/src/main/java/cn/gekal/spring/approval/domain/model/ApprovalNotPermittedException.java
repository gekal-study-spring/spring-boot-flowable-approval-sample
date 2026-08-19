package cn.gekal.spring.approval.domain.model;

/** 承認タスクを操作する権限がないことを表す例外。 */
public class ApprovalNotPermittedException extends RuntimeException {

  public ApprovalNotPermittedException(String message) {
    super(message);
  }
}
