package cn.gekal.spring.approval.domain.model;

import java.util.Objects;

/**
 * 申請金額を表す値オブジェクト（円単位）。
 *
 * <p>金額そのものが持つ制約（正の値であること・上限）をこのクラスで守る。承認者の振り分けルールは金額単体では決まらない（規程の閾値が要る）ため、{@link
 * cn.gekal.spring.approval.domain.service.ExpenseApprovalPolicy} 側に置いている。
 */
public final class ExpenseAmount {

  /** 1件あたりの申請上限。これを超える経費は稟議など別の手続きに回す想定。 */
  public static final long MAX_YEN = 10_000_000L;

  private final long yen;

  private ExpenseAmount(long yen) {
    this.yen = yen;
  }

  public static ExpenseAmount of(long yen) {
    if (yen <= 0) {
      throw new InvalidExpenseRequestException("申請金額は1円以上で指定してください: " + yen);
    }
    if (yen > MAX_YEN) {
      throw new InvalidExpenseRequestException("申請金額が上限（" + MAX_YEN + "円）を超えています: " + yen);
    }
    return new ExpenseAmount(yen);
  }

  public long yen() {
    return yen;
  }

  public boolean isAtLeast(long threshold) {
    return yen >= threshold;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ExpenseAmount other && yen == other.yen;
  }

  @Override
  public int hashCode() {
    return Objects.hash(yen);
  }

  @Override
  public String toString() {
    return yen + "円";
  }
}
