package cn.gekal.spring.approval.presentation.api;

import jakarta.validation.constraints.Size;

/** 承認・却下リクエスト。却下時のコメントはアプリケーション層で必須チェックする。 */
public record ApprovalDecisionRequest(@Size(max = 500) String comment) {}
