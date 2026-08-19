# spring-boot-flowable-approval-sample

Flowable（BPMN 2.0）で経費精算の承認ワークフローを実装した Spring Boot サンプル。

- **10万円未満は課長承認、10万円以上は部長承認**
- **承認時は基幹システムへ自動連携**（非同期 Service Task）
- **却下時は申請者へ通知**
- **3日放置でリマインド送信**（非中断型の境界タイマー、最大3回）

## 技術スタック

| 項目 | 内容 |
| --- | --- |
| 言語 / ビルド | Java 25 / Gradle マルチモジュール（`app` + `migration`） |
| フレームワーク | Spring Boot 4.1（`spring-boot-starter-webmvc`） |
| ワークフロー | Flowable 8.0（`flowable-spring-boot-starter-process`） |
| DB | PostgreSQL 18（テストのみインメモリ H2） |
| マイグレーション | Flyway 13（`migration` モジュールに分離。アプリ起動時には流さない） |
| 認証 | Spring Security（HTTP Basic + インメモリユーザー） |
| コード整形 | Spotless（Google Java Format） |

## ディレクトリ構成

```
app/                                     アプリケーションモジュール
  src/main/java/cn/gekal/spring/approval/
    presentation/                        Controller・DTO・例外ハンドラ
      api/
    application/                         ユースケース（トランザクション境界）
      service/  command/
    domain/                              ビジネスルール（他レイヤーに依存しない）
      model/       ExpenseRequest, ExpenseAmount, ApprovalTask ...
      repository/  Repository インターフェース
      service/     ExpenseApprovalPolicy（承認ルーティング規程）
    infrastructure/                      技術的詳細
      workflow/    Repository 実装（Flowable の Runtime/Task/History/Management Service を隠蔽）
      delegate/    Service Task の JavaDelegate（基幹連携・却下通知・リマインド）
      config/      Security・Bean 定義
  src/main/resources/
    processes/expense-approval.bpmn20.xml  BPMN 2.0 定義（起動時に自動デプロイ）
    application.yaml
  src/test/resources/application-test.yaml  テスト用（H2）

migration/                               DB マイグレーション（Flyway、独立モジュール）
  config/flyway.toml                     環境別ブロック（[environments.dev] など）
  src/main/resources/db/migration/
    schema/                              Flowable のテーブル定義（DDL）
    data/dev/                            環境別データ（DML）

compose.yaml                             postgres → migration → app の順で一式起動
```

## プロセス定義

```
                        ┌─ 10万円未満 ─→ 課長承認 ─┐
開始 → [金額判定] ──────┤                          ├→ [承認判定] ─┬─ 承認 → 基幹システム連携 → 終了
                        └─ 10万円以上 ─→ 部長承認 ─┘              └─ 却下 → 却下通知 → 終了
                                             │
                                    3日経過（非中断・最大3回）
                                             └→ リマインド送信 → 終了
```

| 要素 | ID | 補足 |
| --- | --- | --- |
| Start Event | `startExpenseRequest` | `flowable:initiator="applicantId"` で申請者を記録 |
| Exclusive Gateway | `gatewayAmountCheck` | 条件式 `${expenseApprovalPolicy.requiresDirectorApproval(amount)}` |
| User Task | `userTaskManagerApproval` | `candidateGroups="managers"` |
| User Task | `userTaskDirectorApproval` | `candidateGroups="directors"` |
| Boundary Timer | `timerManagerReminder` / `timerDirectorReminder` | `cancelActivity="false"` + `R3/P3D` |
| Service Task | `serviceTaskErpIntegration` | `flowable:async="true"`（失敗時はジョブリトライ） |
| Service Task | `serviceTaskRejectNotification` / `serviceTask*Reminder` | 通知 |

**金額の閾値はドメインサービス `ExpenseApprovalPolicy` に一本化している。** BPMN の分岐条件も
このクラスの Bean を呼ぶため、「10万円」という規程がコードと BPMN に二重定義されない。

プロセス変数: `applicantId` / `title` / `amount` / `expenseDate` / `category` / `remarks` /
`approved` / `approvalComment` / `approverId` / `erpVoucherNo` / `reminderCount`
（変数名は `ProcessVariables` に集約）

## 起動方法

### Docker Compose（PostgreSQL 込みの一式）

```bash
docker compose up --build
# postgres(15432) → migration(Flyway) → app(18080) の順に起動する
curl -u yamada:password http://localhost:18080/api/expense-requests
```

### ローカル起動（DB だけコンテナ）

```bash
docker compose up -d postgres        # PostgreSQL を起動
./gradlew migration:flywayMigrate    # スキーマを作成（アプリは自分で作らない）
./gradlew app:bootRun                # http://localhost:8080
```

`flowable.database-schema-update: false` にしてあるため、**マイグレーション未実行のまま起動すると
Flowable がスキーマ不一致で起動に失敗する**。これは意図した動作で、スキーマの所有者を Flyway に一本化している。

## ログインユーザー（サンプル用・インメモリ）

| ユーザー | パスワード | 権限（= BPMN の candidateGroups） | 役割 |
| --- | --- | --- | --- |
| `yamada` | `password` | `applicants` | 申請者 |
| `sato` | `password` | `applicants`, `managers` | 課長 |
| `tanaka` | `password` | `applicants`, `directors` | 部長 |

## API

| メソッド | パス | 説明 |
| --- | --- | --- |
| POST | `/api/expense-requests` | 申請を起票してワークフローを開始（201） |
| GET | `/api/expense-requests` | 自分の申請一覧 |
| GET | `/api/expense-requests/{processInstanceId}` | 申請の現在状態 |
| GET | `/api/tasks` | 自分が処理できる承認タスク一覧 |
| POST | `/api/tasks/{taskId}/approve` | 承認 |
| POST | `/api/tasks/{taskId}/reject` | 却下（コメント必須） |
| POST | `/api/demo/reminders/{processInstanceId}` | **動作確認用**: リマインドタイマーを期限切れにする（実行は非同期エグゼキュータが数秒以内に行う） |

リクエスト例は `apis.rest` を参照。

## テスト

```bash
./gradlew test          # 全テスト（H2 で実行。PostgreSQL は不要）
./gradlew spotlessApply # フォーマット適用
./gradlew build         # ビルド + テスト
```

| テスト | 方式 | 対象 |
| --- | --- | --- |
| `ExpenseRequestTest` / `ExpenseApprovalPolicyTest` | 素の JUnit | ドメインの不変条件・承認ルーティングの境界値 |
| `ApprovalTaskServiceTest` | Mockito | ユースケース（引き受け済みタスクの排他、却下コメント必須） |
| `ExpenseRequestApiTest` | MockMvc standalone | 入力検証とエラーレスポンス |
| `ExpenseApprovalProcessTest` | `@SpringBootTest`（非同期エグゼキュータ停止） | プロセス全体の分岐・承認・却下・リマインド |
| `AsyncJobExecutionTest` | `@SpringBootTest`（非同期エグゼキュータ稼働） | 非同期ジョブが実際に実行されること（下記の退行検知） |

## 実装上の注意点

- **非同期ジョブを外側のトランザクションで包まない。** `ManagementService.executeJob()` は
  プロセスインスタンスの排他ロックを自分で取得・解放する。これを Spring の `@Transactional` で
  包むとロックが解放されずに残り、以降の非同期 Service Task が
  `Could not lock process instance` で永久に実行されなくなる（`ReminderTriggerService` のコメント参照）。
- **タイマーと非同期 Service Task には非同期エグゼキュータが必須。** `flowable.async-executor-activate: true`
  を切ると、リマインドも基幹システム連携も動かない。テストでは意図的に切って手動実行している。
- **`flowable:field` の値は `Expression` 型の setter で受ける。** `delegateExpression` で解決される
  Bean はシングルトンなので、注入された値はフィールドに保持したまま使い回さず `execute()` 内で解決する。
- **Flowable のスキーマは Flyway が持つ。** `migration/src/main/resources/db/migration/schema/` の DDL は
  Flowable 8.0.0 の jar に同梱された公式スクリプトをそのまま取り込んだもの。Flowable を上げるときは、
  公式の upgrade スクリプトを新しい `V` ファイルとして追加する（既存ファイルは書き換えない）。

## このサンプルで割り切っている点

- 通知・基幹システム連携は `JavaDelegate` からのログ出力にとどめている（実サービス呼び出しはしない）。
- 認証は HTTP Basic + インメモリユーザー。実案件では OAuth2 リソースサーバ（JWT）に置き換える。
- 承認者の候補グループは Spring Security の権限文字列で表現し、Flowable の IDM テーブルは使っていない。
- `/api/demo/**` は動作確認専用のエンドポイントで、業務用途では公開しない。
- 却下後の再申請（差戻しループ）と、承認者不在時のエスカレーションは未実装。
