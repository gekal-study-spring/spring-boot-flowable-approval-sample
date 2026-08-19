package cn.gekal.spring.approval.domain.repository;

/** リマインドタイマーの操作。3日待たずに動作確認するための強制発火を提供する。 */
public interface ReminderTimerRepository {

  /**
   * 対象プロセスに紐づくリマインドタイマーを即時発火させる。
   *
   * @return 発火させたタイマーの件数
   */
  int fireReminderTimers(String processInstanceId);
}
