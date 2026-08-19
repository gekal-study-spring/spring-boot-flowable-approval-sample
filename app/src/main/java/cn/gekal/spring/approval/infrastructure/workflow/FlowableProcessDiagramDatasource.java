package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.ExpenseRequestNotFoundException;
import cn.gekal.spring.approval.domain.model.ProcessDiagram;
import cn.gekal.spring.approval.domain.repository.ProcessDiagramRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Repository;

/**
 * 承認フローの図に必要な情報を Flowable から取得する実装。
 *
 * <p>画像はサーバで描かず、BPMN の XML と通過済み・実行中の要素IDを返して描画は画面側に任せる。図の見た目を変えるのに
 * サーバの再デプロイが要らず、日本語ラベルのフォントをコンテナに入れる必要もないため。
 */
@Repository
public class FlowableProcessDiagramDatasource implements ProcessDiagramRepository {

  private static final String SEQUENCE_FLOW = "sequenceFlow";

  private final RepositoryService repositoryService;
  private final HistoryService historyService;

  public FlowableProcessDiagramDatasource(
      RepositoryService repositoryService, HistoryService historyService) {
    this.repositoryService = repositoryService;
    this.historyService = historyService;
  }

  @Override
  public ProcessDiagram findDiagram(String processInstanceId) {
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
    if (instance == null) {
      throw new ExpenseRequestNotFoundException(processInstanceId);
    }

    List<HistoricActivityInstance> activities =
        historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();

    return new ProcessDiagram(
        readBpmnXml(instance.getProcessDefinitionId()),
        activities.stream()
            .filter(activity -> !SEQUENCE_FLOW.equals(activity.getActivityType()))
            .filter(activity -> activity.getEndTime() == null)
            .map(HistoricActivityInstance::getActivityId)
            .distinct()
            .toList(),
        activities.stream()
            .filter(activity -> !SEQUENCE_FLOW.equals(activity.getActivityType()))
            .filter(activity -> activity.getEndTime() != null)
            .map(HistoricActivityInstance::getActivityId)
            .distinct()
            .toList(),
        activities.stream()
            .filter(activity -> SEQUENCE_FLOW.equals(activity.getActivityType()))
            .map(HistoricActivityInstance::getActivityId)
            .distinct()
            .toList());
  }

  private String readBpmnXml(String processDefinitionId) {
    try (InputStream stream = repositoryService.getProcessModel(processDefinitionId)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new FlowableException("BPMN 定義の読み込みに失敗しました: " + processDefinitionId, e);
    }
  }
}
