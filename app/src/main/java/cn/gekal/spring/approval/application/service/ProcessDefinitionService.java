package cn.gekal.spring.approval.application.service;

import cn.gekal.spring.approval.domain.model.InvalidProcessDefinitionException;
import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import cn.gekal.spring.approval.domain.repository.ProcessDefinitionRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 承認フロー定義を運用するユースケース。
 *
 * <p>フローの更新にアプリの再起動を要らなくするための入口。配備した時点で新規の起票は新しい版で始まり、走行中の申請は起票時の版のまま完了する。
 *
 * <p>ただし BPMN からアプリ内の Bean を参照している箇所（{@code ${erpIntegrationDelegate}} などの
 * delegateExpression、{@code ${expenseApprovalPolicy...}} の条件式、{@code formKey}、{@code
 * candidateGroups}）は jar の中にあるため、**新しい参照先を持つ BPMN
 * はアプリのデプロイと一緒でないと動かない**。無停止で変えられるのは、既存の参照先の範囲で組み替えられるフローに限る。
 */
@Service
public class ProcessDefinitionService {

  private final ProcessDefinitionRepository processDefinitionRepository;

  public ProcessDefinitionService(ProcessDefinitionRepository processDefinitionRepository) {
    this.processDefinitionRepository = processDefinitionRepository;
  }

  /** BPMN を新しい版として配備する。 */
  public ProcessDefinitionVersion deploy(String resourceName, byte[] bpmnXml, String deployedBy) {
    if (bpmnXml == null || bpmnXml.length == 0) {
      throw new InvalidProcessDefinitionException("BPMN ファイルが空です");
    }
    return processDefinitionRepository.deploy(
        resourceName, bpmnXml, "ApiDeployment(" + deployedBy + ")");
  }

  /** 配備済みのすべてのフローの版を、キーごと・新しい順に返す。 */
  public List<ProcessDefinitionVersion> findVersions() {
    return processDefinitionRepository.findAllVersions();
  }

  /** 指定した版の BPMN XML を返す。 */
  public String findBpmnXml(String processDefinitionId) {
    return processDefinitionRepository.readBpmnXml(processDefinitionId);
  }

  /**
   * 指定した版の内容を新しい版として配備し直す（切り戻し）。
   *
   * <p>Flowable は版を消さずに積み上げる方式なので、古い版へ戻す操作も「その内容で新しい版を作る」形で表す。履歴が消えないぶん、戻した事実も追える。
   */
  public ProcessDefinitionVersion rollbackTo(String processDefinitionId, String deployedBy) {
    ProcessDefinitionVersion source = processDefinitionRepository.find(processDefinitionId);
    String bpmnXml = processDefinitionRepository.readBpmnXml(processDefinitionId);
    // 定義IDは長すぎて一覧で読めないため、戻し先は版数で表す
    return processDefinitionRepository.deploy(
        source.resourceName(),
        bpmnXml.getBytes(StandardCharsets.UTF_8),
        "ApiRollback(" + deployedBy + " -> v" + source.version() + ")");
  }

  /** 指定した版で新規に起票できないようにする。走行中の申請は影響を受けない。 */
  public void suspend(String processDefinitionId) {
    processDefinitionRepository.suspend(processDefinitionId);
  }

  /** 停止した版を再び起票できるようにする。 */
  public void activate(String processDefinitionId) {
    processDefinitionRepository.activate(processDefinitionId);
  }
}
