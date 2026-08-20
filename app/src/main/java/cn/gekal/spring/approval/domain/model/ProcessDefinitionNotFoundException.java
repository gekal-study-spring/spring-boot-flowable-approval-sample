package cn.gekal.spring.approval.domain.model;

/** 指定されたプロセス定義が存在しないことを表す例外。 */
public class ProcessDefinitionNotFoundException extends RuntimeException {

  public ProcessDefinitionNotFoundException(String processDefinitionId) {
    super("プロセス定義が見つかりません: " + processDefinitionId);
  }
}
