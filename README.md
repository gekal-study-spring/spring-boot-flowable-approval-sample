# spring-boot-flowable-approval-sample

Flowable（BPMN 2.0）で経費精算の承認ワークフローを実装した Spring Boot サンプル。

- **10万円未満は課長承認、10万円以上は部長承認**
- **承認時は基幹システムへ自動連携**（非同期 Service Task）
- **却下時は申請者へ通知**
- **3日放置でリマインド送信**（非中断型の境界タイマー、最大3回）
- **承認フローを再起動なしで差し替え**（BPMN を管理APIから配備。走行中の申請は起票時の版のまま完了）

## 技術スタック

| 項目 | 内容 |
| --- | --- |
| 言語 / ビルド | Java 25 / Gradle マルチモジュール（`app` + `migration`） |
| フレームワーク | Spring Boot 4.1（`spring-boot-starter-webmvc`） |
| ワークフロー | Flowable 8.0（`flowable-spring-boot-starter-process`） |
| DB | PostgreSQL 18（テストのみインメモリ H2） |
| マイグレーション | Flyway 13（`migration` モジュールに分離。アプリ起動時には流さない） |
| 認証 | Spring Security（HTTP Basic + インメモリユーザー） |
| 動作確認 GUI | Next.js 16（App Router / 静的書き出し） + MUI 9 |
| GUI の配信 | `web` サービス（nginx）。`https://local.gekal.cn` で開ける。Spring Boot も同じ成果物を同梱して配信する |
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
    processes/expense-approval.bpmn20.xml  BPMN 2.0 定義（初回のみ自動配備。更新は管理画面/管理APIから）
    application.yaml
  src/test/resources/application-test.yaml  テスト用（H2）

web/                                     動作確認コンソール（Next.js 静的書き出し + MUI）
  Dockerfile                             書き出し → nginx（local.gekal.cn の証明書入りイメージ）
  nginx.conf / nginx-locations.conf      静的配信と /api の app へのリバースプロキシ
  src/app/                               ルーティング（単一ページ）
  src/components/{atoms,molecules,organisms,providers}/
  src/lib/                               API クライアント・表示整形（+ node:test）
  → npm run build:app で app/src/main/resources/static/ へも出力できる（app 単体起動用）。
     この出力は生成物なので Git 管理外（.gitignore）

migration/                               DB マイグレーション（Flyway、独立モジュール）
  config/flyway.toml                     環境別ブロック（[environments.dev] など）
  src/main/resources/db/migration/
    schema/                              Flowable のテーブル定義（DDL）
    data/dev/                            環境別データ（DML）

docs/er-diagram.md                       ER 図（データベース構成）
docs/table-reference.md                  テーブル定義と保存されるデータ
docs/flow-definition-management.md       フロー定義の運用（再起動なしの差し替え）
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

## データベース

Flowable が持つスキーマだけで動く（独自の業務テーブルはない）。

- **[docs/er-diagram.md](docs/er-diagram.md)** — ER 図と、どの機能がどのテーブルを読むか
- **[docs/table-reference.md](docs/table-reference.md)** — 1テーブルずつの列定義と、実際に入る値

## フロー定義の差し替え

BPMN は起動時に自動デプロイせず、**管理画面または管理API から明示的に配備する**（`flowable.check-process-definitions: false`）。
動作確認コンソールに admin でログインすると「承認フロー定義」の区画が出て、版の一覧・配備・切り戻し・停止を画面から行える。
配備した時点で新規の起票が新しい版で始まり、**走行中の申請は起票時の版のまま完了する**ため、アプリの再起動は要らない。

ただし BPMN からアプリ内の Bean（`${erpIntegrationDelegate}` などの delegateExpression、`formKey`、
`candidateGroups`）を参照している都合上、**新しい参照先を持つ BPMN はアプリのデプロイと一緒でないと動かない**。
無停止で変えられる範囲と手順は **[docs/flow-definition-management.md](docs/flow-definition-management.md)** にまとめてある。

```bash
curl -u admin:password -X POST http://localhost:18080/api/admin/process-definitions -F "file=@app/src/main/resources/processes/expense-approval.bpmn20.xml"
```

## 承認フロー図

`GET /api/expense-requests/{id}/diagram` は BPMN 2.0 の XML（図形情報つき）と、その申請が
**どこまで進んだか**（実行中・通過済みのアクティビティID、通過したシーケンスフローID）を返す。
GUI では bpmn-visualization（Apache-2.0）で描画し、通過済みを緑、実行中を橙で塗り分ける。「全画面」ボタンで拡大表示できる。

**画像はサーバで生成しない。** Flowable の画像生成（`flowable-image-generator`）を使うと、日本語ラベル用の
フォントをコンテナへ入れる必要があり、見た目を変えるたびにサーバの再デプロイが要るため。XML と進捗だけを
返して描画は画面側に任せている。

## 承認履歴

`flowable.history-level: audit` で記録している Flowable の履歴から、**誰がいつ何をしたか**を組み立てて
返す（`GET /api/expense-requests/{id}/history`、GUI では「承認履歴」のタイムライン）。

```
[申請]     経費精算申請          23:02:30 → 23:02:30
[承認]     課長承認  実施者: sato  23:02:30 → 23:02:34 / 所要 3.4 秒
             └ 「領収書を確認しました」 sato
[自動処理] リマインド送信（課長）  23:02:31 → 23:02:31
[自動処理] 基幹システム連携        23:02:34 → 23:02:34
```

- `ACT_HI_ACTINST` には sequenceFlow やゲートウェイまで残るため、**開始イベント・人手タスク・自動処理だけ**を
  拾っている（`FlowableApprovalHistoryDatasource`）。境界タイマーは待機期間そのものを表す行なので、
  発火の事実は「リマインド送信」の Service Task 側で表す。
- **承認コメントはタスクコメント（`ACT_HI_COMMENT`）として残す。** プロセス変数
  `approvalComment` は「直近の判断内容」で、却下通知の Service Task が読む用途に限る。多段承認や
  差戻しを足したときに変数だと上書きされてしまうため、履歴はタスク側に紐づける。
- 変数の**変更履歴**まで要る場合は `flowable.history-level` を `full` に上げる（`ACT_HI_DETAIL` が増える）。
  実運用では履歴テーブルの肥大化対策（クリーンアップ・アーカイブ）もセットで検討すること。

## 起動方法

### Docker Compose（PostgreSQL 込みの一式）

```bash
docker compose up --build
# postgres → migration(Flyway) → app → web の順に起動する
open https://local.gekal.cn/                 # 動作確認コンソール（GUI）
curl -u yamada:password https://local.gekal.cn/api/expense-requests
```

| サービス | URL | 用途 |
| --- | --- | --- |
| web | `https://local.gekal.cn` | GUI（`/api` は app へ中継する）。**推奨の入口** |
| web | `http://localhost:13000` | 同じ GUI。証明書を気にせず開きたいとき |
| app | `http://localhost:18080` | API 直叩き。GUI も同梱している |
| postgres | `localhost:15432` | DB（user: flowable / pass: secret / db: expense_approval） |

`local.gekal.cn` は公開 DNS が `127.0.0.1` を指しており、証明書は
`gekal/nginx-local-domains:latest-gekal` に同梱された公的に信頼されたものを使う。
hosts ファイルの編集も独自 CA の導入も要らない。

**443 番ポートが他のプロジェクトで使われている場合**は、退避用のポートで起動する:

```bash
WEB_HTTPS_PORT=8443 docker compose up -d   # https://local.gekal.cn:8443/
```

### ローカル起動（DB だけコンテナ）

```bash
docker compose up -d postgres        # PostgreSQL を起動
./gradlew migration:flywayMigrate    # スキーマを作成（アプリは自分で作らない）
./gradlew app:bootRun                # http://localhost:8080（GUI も同じポートで配信される）
```

**app 単体で GUI も開きたい場合**は、先に GUI をビルドしておく（Node が必要）:

```bash
./gradlew app:buildWeb               # web/ をビルドして app の静的リソースへ出力する
./gradlew app:bootRun                # http://localhost:8080 で GUI ごと起動
```

`app/src/main/resources/static/` は生成物なので Git にもコンテナイメージにも含めない。
compose で起動した場合の GUI は `web` サービスが配信する。

`flowable.database-schema-update: false` にしてあるため、**マイグレーション未実行のまま起動すると
Flowable がスキーマ不一致で起動に失敗する**。これは意図した動作で、スキーマの所有者を Flyway に一本化している。

## ログインユーザー（サンプル用・インメモリ）

| ユーザー | パスワード | 権限（= BPMN の candidateGroups） | 役割 |
| --- | --- | --- | --- |
| `yamada` | `password` | `applicants` | 申請者 |
| `sato` | `password` | `applicants`, `managers` | 課長 |
| `tanaka` | `password` | `applicants`, `directors` | 部長 |
| `admin` | `password` | `administrators` | フロー定義の運用者（承認はしない） |

## 動作確認コンソール（GUI）

`https://local.gekal.cn/`（compose）を開くと、ログイン・申請・承認・却下・リマインド発火・
プロセス変数の確認を画面から行える。API 呼び出しと生のレスポンスも画面下部に出る。

`./gradlew app:buildWeb` を実行しておけば、Gradle 単体起動（`http://localhost:8080/`）でも同じ GUI を開ける。
GUI を作り変えたときの手順や開発サーバの使い方は `web/README.md`。

## API

| メソッド | パス | 説明 |
| --- | --- | --- |
| POST | `/api/expense-requests` | 申請を起票してワークフローを開始（201） |
| GET | `/api/expense-requests` | 自分の申請一覧 |
| GET | `/api/expense-requests/{processInstanceId}` | 申請の現在状態 |
| GET | `/api/expense-requests/{processInstanceId}/history` | 承認履歴（誰がいつ何をしたか） |
| GET | `/api/expense-requests/{processInstanceId}/diagram` | 承認フロー図（BPMN 定義 + 通過した経路） |
| GET | `/api/tasks` | 自分が処理できる承認タスク一覧 |
| POST | `/api/tasks/{taskId}/approve` | 承認 |
| POST | `/api/tasks/{taskId}/reject` | 却下（コメント必須） |
| POST | `/api/demo/reminders/{processInstanceId}` | **動作確認用**: リマインドタイマーを期限切れにする（実行は非同期エグゼキュータが数秒以内に行う） |
| GET | `/api/admin/process-definitions` | **管理者のみ**: フロー定義の版一覧（走行中の件数つき） |
| POST | `/api/admin/process-definitions` | **管理者のみ**: BPMN を配備して差し替え（201、再起動不要） |
| GET | `/api/admin/process-definitions/{id}/bpmn` | **管理者のみ**: 指定した版の BPMN XML |
| POST | `/api/admin/process-definitions/{id}/rollback` | **管理者のみ**: 指定した版の内容で配備し直す（201） |
| POST | `/api/admin/process-definitions/{id}/suspend` \| `/activate` | **管理者のみ**: その版での新規起票を停止・再開（204） |
| GET | `/api/me` | ログイン中のユーザーと権限（画面の出し分けに使う） |

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
- GUI は開発用コンソールで、`web/` は静的サイト規約から外して MUI を使い、実行時に API を fetch している。
- 認証は HTTP Basic + インメモリユーザー。実案件では OAuth2 リソースサーバ（JWT）に置き換える。
- 承認者の候補グループは Spring Security の権限文字列で表現し、Flowable の IDM テーブルは使っていない。
- `/api/demo/**` は動作確認専用のエンドポイントで、業務用途では公開しない。
- 却下後の再申請（差戻しループ）と、承認者不在時のエスカレーションは未実装。
