package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import java.time.LocalDateTime;

/** 配備済みプロセス定義1版のレスポンス。 */
public record ProcessDefinitionVersionResponse(
    String processDefinitionId,
    String key,
    String name,
    int version,
    String deploymentId,
    String deploymentName,
    String resourceName,
    LocalDateTime deployedAt,
    boolean suspended,
    boolean latest,
    long runningInstanceCount) {

  public static ProcessDefinitionVersionResponse from(ProcessDefinitionVersion version) {
    return new ProcessDefinitionVersionResponse(
        version.processDefinitionId(),
        version.key(),
        version.name(),
        version.version(),
        version.deploymentId(),
        version.deploymentName(),
        version.resourceName(),
        version.deployedAt(),
        version.suspended(),
        version.latest(),
        version.runningInstanceCount());
  }
}
