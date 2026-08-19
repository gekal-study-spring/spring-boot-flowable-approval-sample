package cn.gekal.spring.approval.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 承認履歴の1件。
 *
 * @param type 種別
 * @param name 表示名（BPMN のアクティビティ名）
 * @param activityId BPMN のアクティビティID
 * @param assignee 実施者。自動処理なら {@code null}
 * @param startedAt 開始日時
 * @param endedAt 完了日時。実行中なら {@code null}
 * @param durationMillis 所要時間（ミリ秒）。実行中なら {@code null}
 * @param comments その承認タスクに残されたコメント（新しい順ではなく記録順）
 */
public record ApprovalHistoryEntry(
    ApprovalHistoryEntryType type,
    String name,
    String activityId,
    String assignee,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long durationMillis,
    List<ApprovalComment> comments) {

  /** 実行中（未完了）か。 */
  public boolean isRunning() {
    return endedAt == null;
  }
}
