package cn.gekal.spring.approval.application.command;

import cn.gekal.spring.approval.domain.model.ApprovalDecision;

/**
 * 承認タスクへの判断入力。
 *
 * @param taskId 対象タスクID
 * @param approverId 操作者（認証済みユーザー）
 * @param decision 承認 / 却下
 * @param comment 承認者コメント
 */
public record ApprovalDecisionCommand(
    String taskId, String approverId, ApprovalDecision decision, String comment) {}
