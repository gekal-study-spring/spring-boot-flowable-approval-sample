package cn.gekal.spring.approval.domain.repository;

import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import java.util.List;

/** 承認フロー定義（BPMN）を配備・照会する。実装はワークフローエンジン側（infrastructure）に置く。 */
public interface ProcessDefinitionRepository {

  /**
   * BPMN を新しい版として配備する。
   *
   * <p>既存の版は消さずに積み上げる。配備が成功した時点から、新規の起票は新しい版で始まる。
   *
   * @param resourceName リソース名。{@code .bpmn20.xml} または {@code .bpmn} で終わる必要がある
   * @param bpmnXml BPMN 2.0 の XML
   * @param deploymentName デプロイ名
   * @return 配備された版
   */
  ProcessDefinitionVersion deploy(String resourceName, byte[] bpmnXml, String deploymentName);

  /** 指定したキーの版を新しい順に返す。 */
  List<ProcessDefinitionVersion> findVersions(String key);

  /** 配備済みのすべての版を、キーごと・新しい順に返す。 */
  List<ProcessDefinitionVersion> findAllVersions();

  /** 指定した版の BPMN XML を返す。 */
  String readBpmnXml(String processDefinitionId);

  /** 指定した版を返す。 */
  ProcessDefinitionVersion find(String processDefinitionId);

  /** 指定した版で新規に起票できないようにする（走行中の申請には影響しない）。 */
  void suspend(String processDefinitionId);

  /** 停止した版を再び起票できるようにする。 */
  void activate(String processDefinitionId);

  /** 指定したキーの定義が1件でも配備されているか。 */
  boolean exists(String key);
}
