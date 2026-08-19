package cn.gekal.spring.approval.domain.model;

/** 承認者のロール。BPMN の candidateGroups と1対1で対応する。 */
public enum ApproverRole {
  /** 課長。10万円未満の申請を承認する。 */
  MANAGER("managers"),
  /** 部長。10万円以上の申請を承認する。 */
  DIRECTOR("directors");

  private final String groupId;

  ApproverRole(String groupId) {
    this.groupId = groupId;
  }

  public String groupId() {
    return groupId;
  }

  public static ApproverRole fromGroupId(String groupId) {
    for (ApproverRole role : values()) {
      if (role.groupId.equals(groupId)) {
        return role;
      }
    }
    throw new IllegalArgumentException("未知の承認グループです: " + groupId);
  }
}
