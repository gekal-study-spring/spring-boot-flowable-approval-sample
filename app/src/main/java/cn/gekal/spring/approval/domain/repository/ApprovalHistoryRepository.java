package cn.gekal.spring.approval.domain.repository;

import cn.gekal.spring.approval.domain.model.ApprovalHistoryEntry;
import java.util.List;

/** 承認履歴の照会。実装はワークフローエンジン側（infrastructure）に置く。 */
public interface ApprovalHistoryRepository {

  /** 申請の履歴を発生順に取得する。 */
  List<ApprovalHistoryEntry> findHistory(String processInstanceId);
}
