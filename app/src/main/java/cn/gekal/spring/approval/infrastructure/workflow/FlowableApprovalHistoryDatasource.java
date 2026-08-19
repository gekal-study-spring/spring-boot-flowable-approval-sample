package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.ApprovalComment;
import cn.gekal.spring.approval.domain.model.ApprovalHistoryEntry;
import cn.gekal.spring.approval.domain.model.ApprovalHistoryEntryType;
import cn.gekal.spring.approval.domain.repository.ApprovalHistoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.springframework.stereotype.Repository;

/**
 * 承認履歴を Flowable の履歴テーブルから組み立てる実装。
 *
 * <p>{@code ACT_HI_ACTINST} には sequenceFlow やゲートウェイまで残るため、人が読む履歴としては
 * 開始イベント・人手タスク・自動処理だけを拾う。境界タイマー（3日経過リマインド）は待機期間そのものを表す行で、 発火の事実は「リマインド送信」の Service Task
 * 側に出るため重複させない。
 */
@Repository
public class FlowableApprovalHistoryDatasource implements ApprovalHistoryRepository {

  /** 履歴として表示する BPMN のアクティビティ種別。 */
  private static final Set<String> VISIBLE_ACTIVITY_TYPES =
      Set.of("startEvent", "userTask", "serviceTask");

  private final HistoryService historyService;
  private final TaskService taskService;

  public FlowableApprovalHistoryDatasource(HistoryService historyService, TaskService taskService) {
    this.historyService = historyService;
    this.taskService = taskService;
  }

  @Override
  public List<ApprovalHistoryEntry> findHistory(String processInstanceId) {
    return historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .list()
        .stream()
        .filter(activity -> VISIBLE_ACTIVITY_TYPES.contains(activity.getActivityType()))
        .sorted(Comparator.comparing(HistoricActivityInstance::getStartTime))
        .map(this::toEntry)
        .toList();
  }

  private ApprovalHistoryEntry toEntry(HistoricActivityInstance activity) {
    return new ApprovalHistoryEntry(
        typeOf(activity),
        activity.getActivityName(),
        activity.getActivityId(),
        activity.getAssignee(),
        FlowableExpenseRequestDatasource.toLocalDateTime(activity.getStartTime()),
        FlowableExpenseRequestDatasource.toLocalDateTime(activity.getEndTime()),
        activity.getDurationInMillis(),
        commentsOf(activity.getTaskId()));
  }

  private static ApprovalHistoryEntryType typeOf(HistoricActivityInstance activity) {
    return switch (activity.getActivityType()) {
      case "startEvent" -> ApprovalHistoryEntryType.APPLICATION;
      case "userTask" -> ApprovalHistoryEntryType.APPROVAL_TASK;
      default -> ApprovalHistoryEntryType.SYSTEM_TASK;
    };
  }

  private List<ApprovalComment> commentsOf(String taskId) {
    if (taskId == null) {
      return List.of();
    }
    return taskService.getTaskComments(taskId).stream()
        .map(
            comment ->
                new ApprovalComment(
                    comment.getUserId(),
                    comment.getFullMessage(),
                    FlowableExpenseRequestDatasource.toLocalDateTime(comment.getTime())))
        .toList();
  }
}
