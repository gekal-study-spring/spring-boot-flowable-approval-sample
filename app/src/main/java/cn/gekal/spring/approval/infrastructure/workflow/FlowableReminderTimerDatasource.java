package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.repository.ReminderTimerRepository;
import java.util.List;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Repository;

/**
 * リマインドタイマーを Flowable のジョブとして操作する実装。
 *
 * <p>タイマーは3日後に発火するため、通常は未到来のタイマージョブとして保持されている。ここではそれを実行可能ジョブへ移送して、期限が来た状態を作り出す。
 *
 * <p>移送後の実行はエンジンに任せる（非同期エグゼキュータが拾う）。自分でも実行すると同じジョブを二重に処理して {@code
 * FlowableOptimisticLockingException} になるため、エグゼキュータが止まっている場合（テストなど）に限って直接実行する。
 */
@Repository
public class FlowableReminderTimerDatasource implements ReminderTimerRepository {

  private final ManagementService managementService;
  private final ProcessEngine processEngine;

  public FlowableReminderTimerDatasource(
      ManagementService managementService, ProcessEngine processEngine) {
    this.managementService = managementService;
    this.processEngine = processEngine;
  }

  @Override
  public int fireReminderTimers(String processInstanceId) {
    List<Job> timerJobs =
        managementService.createTimerJobQuery().processInstanceId(processInstanceId).list();
    for (Job timerJob : timerJobs) {
      Job executable = managementService.moveTimerToExecutableJob(timerJob.getId());
      if (!isAsyncExecutorActive()) {
        managementService.executeJob(executable.getId());
      }
    }
    return timerJobs.size();
  }

  private boolean isAsyncExecutorActive() {
    var asyncExecutor = processEngine.getProcessEngineConfiguration().getAsyncExecutor();
    return asyncExecutor != null && asyncExecutor.isActive();
  }
}
