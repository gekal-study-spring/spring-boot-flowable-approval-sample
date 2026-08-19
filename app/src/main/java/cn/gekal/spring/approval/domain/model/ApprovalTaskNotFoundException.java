package cn.gekal.spring.approval.domain.model;

/** 指定された承認タスクが存在しないことを表す例外。 */
public class ApprovalTaskNotFoundException extends RuntimeException {

  public ApprovalTaskNotFoundException(String taskId) {
    super("承認タスクが見つかりません: " + taskId);
  }
}
