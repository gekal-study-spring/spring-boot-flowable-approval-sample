package cn.gekal.spring.approval.application.service;

import cn.gekal.spring.approval.domain.repository.ReminderTimerRepository;
import org.springframework.stereotype.Service;

/**
 * リマインドの動作確認用ユースケース。3日の経過を待たずにタイマーを発火させる。
 *
 * <p>ジョブ実行はワークフローエンジンが自前のトランザクションで制御する（実行の前後でプロセスインスタンスの排他ロックを取得・解放する）。ここで {@code @Transactional}
 * を付けて外側のトランザクションに巻き込むと、ロックが解放されないまま残り、以降の非同期ジョブが 「Could not lock process
 * instance」で永久に実行できなくなるため、意図的に付けていない。
 */
@Service
public class ReminderTriggerService {

  private final ReminderTimerRepository reminderTimerRepository;

  public ReminderTriggerService(ReminderTimerRepository reminderTimerRepository) {
    this.reminderTimerRepository = reminderTimerRepository;
  }

  public int fireReminders(String processInstanceId) {
    return reminderTimerRepository.fireReminderTimers(processInstanceId);
  }
}
