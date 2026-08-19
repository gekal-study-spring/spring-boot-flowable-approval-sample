package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ProcessDiagram;
import java.util.List;

/** 承認フローの図を描くためのレスポンス。 */
public record ProcessDiagramResponse(
    String bpmnXml,
    List<String> currentActivityIds,
    List<String> completedActivityIds,
    List<String> takenFlowIds) {

  public static ProcessDiagramResponse from(ProcessDiagram diagram) {
    return new ProcessDiagramResponse(
        diagram.bpmnXml(),
        diagram.currentActivityIds(),
        diagram.completedActivityIds(),
        diagram.takenFlowIds());
  }
}
