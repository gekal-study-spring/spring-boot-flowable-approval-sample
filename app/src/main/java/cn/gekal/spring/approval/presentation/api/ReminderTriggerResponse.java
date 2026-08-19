package cn.gekal.spring.approval.presentation.api;

/** リマインド強制発火の結果。 */
public record ReminderTriggerResponse(String processInstanceId, int firedTimers) {}
