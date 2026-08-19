package cn.gekal.spring.approval.domain.repository;

import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import java.util.List;
import java.util.Optional;

/** 経費精算申請の永続化・照会。実装はワークフローエンジン側（infrastructure）に置く。 */
public interface ExpenseRequestRepository {

  /**
   * 申請を登録し、承認ワークフローを開始する。
   *
   * @return プロセスインスタンスIDが確定した申請
   */
  ExpenseRequest start(ExpenseRequest request);

  /** プロセスインスタンスIDで現在状態を取得する。存在しないことが正常系のため Optional を返す。 */
  Optional<ExpenseRequestState> findState(String processInstanceId);

  /** 申請者の申請一覧を新しい順で取得する。 */
  List<ExpenseRequestState> findByApplicant(String applicantId);
}
