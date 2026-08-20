package cn.gekal.spring.approval.presentation.api;

import java.util.List;

/**
 * ログイン中のユーザー。
 *
 * @param userId ユーザーID
 * @param authorities 権限（BPMN の candidateGroups と一致する）
 * @param canManageProcessDefinitions フロー定義を運用できるか
 */
public record CurrentUserResponse(
    String userId, List<String> authorities, boolean canManageProcessDefinitions) {}
