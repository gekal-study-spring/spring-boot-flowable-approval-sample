package cn.gekal.spring.approval.domain.repository;

import cn.gekal.spring.approval.domain.model.ProcessDiagram;

/** 承認フローの図を組み立てるための情報を取得する。実装はワークフローエンジン側（infrastructure）に置く。 */
public interface ProcessDiagramRepository {

  /** 指定した申請の BPMN 定義と進捗状況を取得する。 */
  ProcessDiagram findDiagram(String processInstanceId);
}
