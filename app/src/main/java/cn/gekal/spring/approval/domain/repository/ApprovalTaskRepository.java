package cn.gekal.spring.approval.domain.repository;

import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 承認タスクの照会・完了。実装はワークフローエンジン側（infrastructure）に置く。 */
public interface ApprovalTaskRepository {

  /** 指定ユーザーが処理できる承認タスク（所属グループ宛て、または自分が引き受け済み）を取得する。 */
  List<ApprovalTask> findOperableTasks(String userId, Collection<String> groupIds);

  /** タスクIDで1件取得する。 */
  Optional<ApprovalTask> findById(String taskId);

  /** 承認・却下してタスクを完了する。未引き受けなら引き受けてから完了する。 */
  void complete(String taskId, String approverId, ApprovalDecision decision, String comment);
}
