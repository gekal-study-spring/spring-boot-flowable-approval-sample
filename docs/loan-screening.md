# 個人ローン審査プロセス

経費精算（`expenseApprovalProcess`）が単純な多段承認の例なのに対し、こちらは実務で出てくる構造を
ひと通り含めた例として置いている。両方が同じアプリに同居しており、片方を読んでから比べられる。

- プロセス定義キー: `loanScreeningProcess`
- 定義ファイル: [`app/src/main/resources/processes/loan-screening.bpmn20.xml`](../app/src/main/resources/processes/loan-screening.bpmn20.xml)
- 現状は **BPMN の骨格まで**。外部連携はスタブ、申込の入口（API・画面）はまだ無い

## フロー

```
ローン申込受付
  ↓
書類確認 ──不備あり──→ 追加書類の提出（申込者）──┐
  ↓ 不備なし                                   └→ 書類確認へ戻る
  ├─並行─→ 信用情報照会
  └─並行─→ 反社・制裁リスト照合
  ↓ 両方そろうまで待つ
与信スコアリング
  ↓
├ 自動謝絶 ─────────────────→ 謝絶通知 → 謝絶
├ 自動承認 ─────────────────→ 契約書作成へ
└ 人手審査 → 審査担当者 → 課長決裁 →（500万円以上なら）部長決裁 → 最終判定
                                                    ├ 承認 → 契約書作成へ
                                                    └ 否決 → 謝絶通知へ
契約書作成 → 顧客の契約締結 ──14日で失効──→ 期限切れ失効
                  ↓ 締結
              融資実行 → 融資実行完了
```

## 経費精算との違い

この5点がこのプロセスを置いている理由。

| 構造 | 使っている BPMN 要素 | 経費精算 |
| --- | --- | --- |
| **差戻しループ** | 追加書類の提出から書類確認へ戻すシーケンスフロー | 無し |
| **並行実行と合流** | `parallelGateway` 2つ | 無し |
| **自動判定の三分岐** | `exclusiveGateway` + スコアリングの結果 | 金額だけの二分岐 |
| **段数が変わる決裁** | 決裁区分の `exclusiveGateway` で部長決裁を挟むか決める | 段数固定 |
| **中断型タイマー** | `cancelActivity="true"` の境界イベント | 非中断型のみ（リマインド） |

**中断型と非中断型の違い**がこの2つを並べる一番の見どころになる。経費精算のリマインドはタスクを
残したまま枝が走る（`cancelActivity="false"`）のに対し、契約締結の期限は発火するとタスクごと
打ち切って失効させる。

## 判定ロジック

[`LoanScreeningPolicy`](../app/src/main/java/cn/gekal/spring/approval/domain/service/LoanScreeningPolicy.java)（ドメインサービス）に閉じ込めている。

| 規程 | 値 |
| --- | --- |
| 部長決裁が必要な金額 | 500万円以上 |
| 自動承認の上限額 | 100万円 |
| 自動承認に必要なスコア | 700 以上 |
| 自動謝絶になるスコア | 400 以下 |
| 年収倍率の上限 | 3.0 倍（超えたら人手審査） |

制裁リストに該当した場合はスコアによらず謝絶する。BPMN の決裁区分の分岐も
`${loanScreeningPolicy.requiresExecutiveApproval(requestedAmountYen)}` でこのクラスを呼ぶ。

## 外部連携はスタブ

| Service Task | 実装 | 振る舞い |
| --- | --- | --- |
| 信用情報照会 | `CreditBureauInquiryDelegate` | 申込者IDから決まる 300〜850 のスコアを返す |
| 反社・制裁リスト照合 | `SanctionScreeningDelegate` | 申込者IDが `sanctioned` で始まるときだけヒット |
| 与信スコアリング | `CreditScoringDelegate` | `LoanScreeningPolicy` を呼んで判定区分を決める |
| 契約書作成 | `ContractPreparationDelegate` | 契約番号を採番する |
| 融資実行 | `LoanExecutionDelegate` | ログのみ |
| 謝絶通知 | `DeclineNotificationDelegate` | ログのみ |

**スコアが乱数でなく申込者IDから決まる**のは、テストでルートを選べるようにするため。
`yamada` は中位スコアで人手審査、`watanabe` は高スコアで自動承認、`sanctioned-*` は自動謝絶になる。

外部照会の2つは `flowable:async="true"` かつ `exclusive="false"` で、並行に走る。融資実行は
金銭が動くため `exclusive="true"` で直列化している。

## 次にやること

現状は骨格だけなので、実用へ寄せるには以下が要る。**上から順に効果が大きい。**

1. **申込データを業務テーブルへ移す。** いまは申込内容をプロセス変数に載せているが、個人情報が
   `ACT_HI_VARINST` に残り続け、履歴のクリーンアップと保存要件が衝突する。業務テーブル
   （Flyway + MyBatis）に置き、プロセス変数には `applicationId` だけを持たせる。
   検索・集計・1対多（提出書類、照会結果の履歴）・長期保管のいずれも業務テーブルが要る
   （[ER 図の「業務テーブルを持たない理由」](er-diagram.md#業務テーブルを持たない理由)に挙げた3条件を
   すべて満たす）
2. **与信判定を DMN の決定表へ。** 金額 × スコアの表はコードより表のほうが読みやすく、
   [管理画面](flow-definition-management.md)と同じ要領で**審査基準を無停止で差し替えられる**。
   DMN エンジンの依存追加が要る
3. **申込の入口と審査画面**（API・GUI）。承認者グループ `loanClerks` / `loanAnalysts` /
   `loanManagers` / `loanExecutives` に対応するユーザーもこの段階で足す
4. **外部連携の失敗設計**。リトライ回数、デッドレターの監視、補償処理
5. **合議と条件付き承認**。複数人の決裁（multi-instance）、減額・金利変更を伴う承認

## テスト

[`LoanScreeningProcessTest`](../app/src/test/java/cn/gekal/spring/approval/workflow/LoanScreeningProcessTest.java)
が骨格の全ルートを通している。入口がまだ無いため、Flowable のサービスを直接叩いている。

| テスト | 確かめていること |
| --- | --- |
| 書類不備の差戻し | 申込者へ戻り、再提出で書類確認へ復帰し、何度でも回れる |
| 並行照会 | 2つの照会が同時にジョブとして待機し、両方そろってから先へ進む |
| 自動謝絶 | リスト照合に該当すると人手を介さず `endEventDeclined` で終わる |
| 自動承認 | 少額かつ高スコアなら審査タスクを挟まず契約締結まで進む |
| 部長決裁あり | 500万円以上で課長のあとに部長決裁が入り、`endEventFunded` まで到達する |
| 部長決裁なし | 500万円未満では部長決裁を飛ばす |
| 人手否決 | 否決すると謝絶へ回る |
| 期限切れ | 中断型タイマーが契約締結タスクを打ち切り、`endEventExpired` で終わる |
