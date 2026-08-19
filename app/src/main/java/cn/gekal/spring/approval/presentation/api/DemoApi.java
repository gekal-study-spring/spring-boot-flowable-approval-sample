package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.application.service.ReminderTriggerService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 動作確認用 API。
 *
 * <p>3日待たずにリマインドの動きを確かめるためのもので、業務用途では公開しない。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoApi {

  private final ReminderTriggerService reminderTriggerService;

  public DemoApi(ReminderTriggerService reminderTriggerService) {
    this.reminderTriggerService = reminderTriggerService;
  }

  /** 対象申請のリマインドタイマーを即時発火させる。 */
  @PostMapping("/reminders/{processInstanceId}")
  public ReminderTriggerResponse fireReminders(@PathVariable String processInstanceId) {
    return new ReminderTriggerResponse(
        processInstanceId, reminderTriggerService.fireReminders(processInstanceId));
  }
}
