package cn.gekal.spring.approval.domain.model;

/** デプロイしようとした BPMN 定義が受け入れられないことを表す例外。 */
public class InvalidProcessDefinitionException extends RuntimeException {

  public InvalidProcessDefinitionException(String message) {
    super(message);
  }

  public InvalidProcessDefinitionException(String message, Throwable cause) {
    super(message, cause);
  }
}
