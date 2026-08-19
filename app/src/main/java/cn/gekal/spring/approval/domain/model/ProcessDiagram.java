package cn.gekal.spring.approval.domain.model;

import java.util.List;

/**
 * 承認フローを図として描くための情報。
 *
 * <p>BPMN 定義そのものと、その申請がどこまで進んだかを渡し、描画は画面側に任せる。
 *
 * @param bpmnXml プロセス定義の BPMN 2.0 XML（図形情報つき）
 * @param currentActivityIds 実行中のアクティビティID
 * @param completedActivityIds 通過済みのアクティビティID
 * @param takenFlowIds 通過済みのシーケンスフローID（どちらへ分岐したかが分かる）
 */
public record ProcessDiagram(
    String bpmnXml,
    List<String> currentActivityIds,
    List<String> completedActivityIds,
    List<String> takenFlowIds) {}
