# テーブル定義と保存されるデータ

[ER 図](er-diagram.md)がテーブル同士の関係を扱うのに対し、こちらは**1テーブルずつの列定義**と、
**実際にどんな値が入るのか**をまとめる。値の例はすべて稼働中の PostgreSQL から採取した実データ。

- DBMS: PostgreSQL 18（スキーマ `public`）
- Flowable 8.0.0 が定義するテーブルのみ。独自の業務テーブルはない
- 45 テーブルのうち、この経費精算アプリが実際に読み書きするのは **16 テーブル**

## スキーマの作られ方

アプリは `flowable.database-schema-update: false` で起動し、自分ではスキーマを触らない。
DDL は `migration` モジュール（Flyway）が持つ。

| スクリプト | テーブル数 | 内容 |
| --- | --- | --- |
| `V1_0_0__flowable_common.sql` | 20 | 共通（変数・タスク・ジョブ・識別リンク） |
| `V1_0_1__flowable_engine.sql` | 7 | BPMN エンジン（定義・実行） |
| `V1_0_2__flowable_history.sql` | 5 | 履歴 |
| `V1_0_3__flowable_identity.sql` | 9 | IDM（**本アプリは未使用**） |
| `V1_0_4__flowable_eventregistry.sql` | 4 | イベントレジストリ（**本アプリは未使用**） |
| `V9_0_0__dev_seed.sql` | – | 開発用 DML（現状は投入データなし） |

Flowable の DDL は「共通 → エンジン → 履歴」の順に依存するため、この分割順を変えると流れない。

## 列の共通ルール

Flowable のテーブルには全体を貫く命名の約束がある。個別表で毎回説明しないので先に挙げる。

| 列 | 型 | 意味 |
| --- | --- | --- |
| `ID_` | `varchar(64)` | 主キー。UUID v1（例 `775a37c3-9c4a-11f1-b867-d2a3a68fa4db`） |
| `REV_` | `integer` | 楽観ロック用のリビジョン。更新のたびに +1 され、`UPDATE ... WHERE REV_=?` で衝突を検出する |
| `PROC_INST_ID_` | `varchar(64)` | 所属するプロセスインスタンス。**申請1件の識別子**にあたる |
| `PROC_DEF_ID_` | `varchar(64)` | プロセス定義。`expenseApprovalProcess:1:da915b86-…` の形 |
| `EXECUTION_ID_` | `varchar(64)` | 実行の枝（後述の `ACT_RU_EXECUTION`） |
| `TENANT_ID_` | `varchar(255)` | マルチテナント用。本アプリは常に空文字 |
| `SCOPE_ID_` / `SCOPE_TYPE_` ほか | `varchar` | CMMN（ケース管理）と共用するための列。BPMN だけを使う本アプリでは常に NULL |

- 列名の**末尾がアンダースコア**なのは、DB の予約語との衝突を避けるための Flowable の流儀
- タイムスタンプはすべて `timestamp without time zone`。JVM とコンテナの `TZ: Asia/Tokyo` で解釈される

---

# 1. 定義（デプロイした BPMN）

**配備のたびに版が積み上がる**。初回起動で同梱の BPMN が1版入り、以後は管理API から配備する
（[フロー定義の運用](flow-definition-management.md)）。走行中の申請が起票時の版を参照し続けるため、古い版も残す。

## ACT_RE_DEPLOYMENT — デプロイ単位（1行）

| 列 | 型 | 保存される内容 | 実データ |
| --- | --- | --- | --- |
| `ID_` | varchar(64) | デプロイID | `da6a4b83-9bd3-11f1-…` |
| `NAME_` | varchar(255) | デプロイ名 | `SpringBootAutoDeployment` |
| `DEPLOY_TIME_` | timestamp | デプロイ日時 | `2026-08-19 22:42:42.999` |
| `PARENT_DEPLOYMENT_ID_` | varchar(255) | 親デプロイ。単体デプロイでは自分自身 | 自 ID と同じ |
| `CATEGORY_` / `KEY_` / `TENANT_ID_` / `DERIVED_FROM_` / `DERIVED_FROM_ROOT_` / `ENGINE_VERSION_` | varchar | 未使用 | 空 |

`NAME_` には配備の由来が入る。同梱 BPMN の初回配備は `PackagedBootstrap`、管理API からは
`ApiDeployment(admin)` / `ApiRollback(admin -> ...)`。**一覧を見れば誰がどう入れたか追える**。
起動時オートデプロイを使っていた頃の行は `SpringBootAutoDeployment` のまま残る。

## ACT_RE_PROCDEF — プロセス定義（1行）

| 列 | 型 | 保存される内容 | 実データ |
| --- | --- | --- | --- |
| `ID_` | varchar(64) | `キー:バージョン:UUID` の複合形式 | `expenseApprovalProcess:1:da915b86-…` |
| `KEY_` | varchar(255) | BPMN の `process id` | `expenseApprovalProcess` |
| `NAME_` | varchar(255) | BPMN の `process name` | `経費精算承認プロセス` |
| `VERSION_` | integer | 同一キー内の版数。配備のたびに 2, 3 … と増える | `1` |
| `DEPLOYMENT_ID_` | varchar(64) | 親デプロイ（FK） | `da6a4b83-…` |
| `RESOURCE_NAME_` | varchar(4000) | XML のリソース名。**ディレクトリ名は含まない** | `expense-approval.bpmn20.xml` |
| `DGRM_RESOURCE_NAME_` | varchar(4000) | 自動生成された図の名前 | `expense-approval.expenseApprovalProcess.png` |
| `HAS_GRAPHICAL_NOTATION_` | boolean | BPMNDI（座標情報）を持つか | `true` |
| `HAS_START_FORM_KEY_` | boolean | 開始イベントに `formKey` があるか | `false` |
| `SUSPENSION_STATE_` | integer | `1`=有効 / `2`=停止。停止中は新規開始できない | `1` |

新しい申請は常に**最新バージョン**で開始される。エンジンは起票のたびに DB から最新版を引き直すので、
**配備した時点でアプリの再起動なしに切り替わる**。実行中の申請は開始時のバージョンのまま走り続けるため、
BPMN を変更しても走行中のものは影響を受けない。

## ACT_GE_BYTEARRAY — バイナリの実体（2行）

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `ID_` | varchar(64) | – |
| `NAME_` | varchar(255) | リソース名 |
| `DEPLOYMENT_ID_` | varchar(64) | 親デプロイ（FK） |
| `BYTES_` | bytea | 実体 |
| `GENERATED_` | boolean | Flowable が生成したものか |

| `NAME_` | サイズ | `GENERATED_` | 用途 |
| --- | --- | --- | --- |
| `expense-approval.bpmn20.xml` | 14,244 B | `false` | **BPMN XML の原本**。`GET /api/…/diagram` がここから読む |
| `expense-approval.expenseApprovalProcess.png` | 22,010 B | `true` | 起動時に自動生成された図。本アプリは使っていない（画面はブラウザ側で XML から描く） |

このテーブルは**長い文字列のプロセス変数の退避先**も兼ねる（`varchar(4000)` に収まらない値は
ここへ逃がされ、変数側は `BYTEARRAY_ID_` で参照する）。本アプリの変数はすべて短いため発生していない。

## ACT_GE_PROPERTY — エンジンの設定値（7行）

`NAME_` / `VALUE_` / `REV_` の3列だけ。全行を挙げる。

| `NAME_` | `VALUE_` | 意味 |
| --- | --- | --- |
| `schema.version` | `8.0.0.0` | エンジンのスキーマ版。起動時に jar の版と照合され、食い違うと起動が落ちる |
| `common.schema.version` | `8.0.0.0` | 共通スキーマ版 |
| `eventregistry.schema.version` | `8.0.0.0` | イベントレジストリのスキーマ版 |
| `schema.history` | `create(8.0.0.0)` | スキーマの作成・更新の履歴 |
| `next.dbid` | `1` | ID ジェネレータの採番位置。UUID 採番のため進まない |
| `cfg.execution-related-entities-count` | `true` | 実行の件数カウント列（`TASK_COUNT_` など）を使うか |
| `cfg.task-related-entities-count` | `true` | タスクの件数カウント列を使うか |

**Flowable のバージョンを上げるときに最初に見る場所**。ここの版と jar の版が合わない状態で
`database-schema-update: false` のまま起動すると、明示的なエラーで止まる。

---

# 2. 実行時（承認待ちの間だけ存在）

`ACT_RU_*` は**処理中の申請だけ**が持つ行で、プロセスが完了すると**すべて削除される**。
削除されても履歴（`ACT_HI_*`）は残るので、完了した申請の情報が失われるわけではない。

## ACT_RU_EXECUTION — 実行の枝（1申請あたり 2〜3行）

39 列あるが、大半は他機能用。**この列が本体**というものだけ挙げる。

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `ID_` | varchar(64) | 実行ID。ルート行では `PROC_INST_ID_` と同値 |
| `PROC_INST_ID_` | varchar(64) | プロセスインスタンスID = **申請の識別子** |
| `PARENT_ID_` | varchar(64) | 親の実行。ルートは NULL |
| `ACT_ID_` | varchar(255) | いま止まっている BPMN 要素のID。ルート行では NULL |
| `IS_ACTIVE_` | boolean | この枝が生きているか |
| `IS_SCOPE_` | boolean | 変数を持てる階層か。ルート行のみ `true` |
| `IS_CONCURRENT_` | boolean | 並行分岐中か |
| `START_ACT_ID_` | varchar(255) | 開始した要素（ルート行のみ）= `startExpenseRequest` |
| `START_TIME_` / `START_USER_ID_` | timestamp / varchar | 開始日時・開始者（ルート行のみ） |
| `SUSPENSION_STATE_` | integer | `1`=実行中 / `2`=一時停止 |
| `TASK_COUNT_` / `TIMER_JOB_COUNT_` / `VAR_COUNT_` / `ID_LINK_COUNT_` ほか | integer | 子エンティティの件数キャッシュ。**不要な SELECT を省くための非正規化**で、`cfg.*-related-entities-count` が `true` のときだけ維持される |
| `BUSINESS_KEY_` / `NAME_` / `CALLBACK_*` / `REFERENCE_*` / `SUPER_EXEC_` ほか | varchar | 本アプリでは常に NULL |

課長承認待ちの申請の実データ。**3行で1申請**を表す。

| `ID_` | `PARENT_ID_` | `ACT_ID_` | `IS_SCOPE_` | 役割 |
| --- | --- | --- | --- | --- |
| `286cd578-…`（= `PROC_INST_ID_`） | NULL | NULL | `true` | **ルート**。変数と開始情報を持つ |
| `286cfc91-…` | ルート | `userTaskManagerApproval` | `false` | 承認タスクで待っている枝 |
| `286d23a7-…` | `286cfc91-…` | `timerManagerReminder` | `false` | 境界タイマーが張り付いた枝 |

境界イベントが**独立した子の実行**として存在するのがポイントで、これが「タスクと並行にタイマーが動く」
非中断型の実体になっている。

## ACT_RU_TASK — 承認待ちタスク（1申請あたり 1行）

37 列のうち、本アプリで値が入るのは以下だけ。

| 列 | 型 | 保存される内容 | 実データ |
| --- | --- | --- | --- |
| `ID_` | varchar(64) | タスクID。API の `{taskId}` | `286d23a9-…` |
| `EXECUTION_ID_` | varchar(64) | 属する実行の枝 | `286cfc91-…` |
| `PROC_INST_ID_` / `PROC_DEF_ID_` | varchar(64) | 申請・定義 | – |
| `TASK_DEF_KEY_` | varchar(255) | BPMN の `userTask id`。**課長／部長の判別に使う** | `userTaskManagerApproval` |
| `NAME_` | varchar(255) | 表示名 | `課長承認` |
| `STATE_` | varchar(255) | タスク状態 | `created` |
| `FORM_KEY_` | varchar(255) | BPMN の `formKey` | `expenseApprovalForm` |
| `CREATE_TIME_` | timestamp | 発生日時。一覧の並び順に使う | `2026-08-20 12:49:34.495` |
| `PRIORITY_` | integer | 優先度。既定値のまま | `50` |
| `SUSPENSION_STATE_` | integer | `1`=実行中 | `1` |
| `ID_LINK_COUNT_` | integer | 識別リンクの件数 | `1` |
| `ASSIGNEE_` / `CLAIM_TIME_` / `CLAIMED_BY_` | varchar / timestamp | **承認待ちの間は常に NULL** | 空 |
| `DUE_DATE_` / `OWNER_` / `PARENT_TASK_ID_` / `CATEGORY_` / `DESCRIPTION_` ほか | – | 本アプリでは未使用 | 空 |

`ASSIGNEE_` が空なのは、**タスクを個人に割り当てず候補グループ宛てのまま置いている**ため。
承認操作の直前に `taskService.claim()` して担当者を確定させ、そのまま完了する
（[FlowableApprovalTaskDatasource.java](../app/src/main/java/cn/gekal/spring/approval/infrastructure/workflow/FlowableApprovalTaskDatasource.java) の `complete`）。
このやり方だと「誰でも取れるが、取った人が履歴に残る」動きになる。

## ACT_RU_VARIABLE — プロセス変数（1申請あたり 7行〜）

**申請内容そのものが入るテーブル**。列ではなく行で持つ縦持ち構造。

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `NAME_` | varchar(255) | 変数名（`title`, `amount` …） |
| `TYPE_` | varchar(255) | 型名。`string` / `long` / `integer` / `boolean` など |
| `EXECUTION_ID_` | varchar(64) | どの枝に属するか。プロセス変数はルート実行に付く |
| `PROC_INST_ID_` | varchar(64) | 申請 |
| `TASK_ID_` | varchar(64) | タスクローカル変数のときだけ。本アプリは常に NULL |
| `TEXT_` | varchar(4000) | 文字列値。**数値型でも文字列表現が併記される** |
| `LONG_` | bigint | 整数値・真偽値（`0` / `1`） |
| `DOUBLE_` | double | 小数値。本アプリは未使用 |
| `BYTEARRAY_ID_` | varchar(64) | 4000 文字を超える値の退避先。本アプリは未使用 |
| `TEXT2_` / `META_INFO_` | varchar(4000) | JSON 型などの補助情報。本アプリは未使用 |

課長承認待ちの実データ（起票時点で 7 行）。

| `NAME_` | `TYPE_` | `LONG_` | `TEXT_` |
| --- | --- | --- | --- |
| `applicantId` | string | | `yamada` |
| `title` | string | | `タイマー検証` |
| `amount` | long | `30000` | `30000` |
| `expenseDate` | string | | `2026-08-20` |
| `category` | string | | `会議費` |
| `remarks` | string | | `リマインド繰り返しの確認` |
| `reminderCount` | integer | `3` | `3` |

承認・却下すると `approved`(boolean) / `approvalComment`(string) / `approverId`(string) が加わり、
承認完了時に `erpVoucherNo`(string) が加わって**最大 11 行**になる。

- **数値型は `LONG_` と `TEXT_` の両方に入る**。検索は `LONG_`、表示は `TEXT_` が使われる
- `boolean` は `LONG_` に `0`/`1` が入り、`TEXT_` は空
- 変数名は [`ProcessVariables`](../app/src/main/java/cn/gekal/spring/approval/infrastructure/workflow/ProcessVariables.java) に集約している。BPMN の条件式（`${approved}`）とここの文字列が一致していないと実行時まで気付けない

## ACT_RU_IDENTITYLINK — 誰がこの申請に関わるか（1申請あたり 2行）

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `TYPE_` | varchar(255) | 関与の種類。`starter` / `candidate` / `assignee` / `participant` |
| `USER_ID_` | varchar(255) | 個人を指すとき |
| `GROUP_ID_` | varchar(255) | グループを指すとき |
| `TASK_ID_` | varchar(64) | タスクに紐づくリンク |
| `PROC_INST_ID_` | varchar(64) | プロセスに紐づくリンク |

| `TYPE_` | `USER_ID_` | `GROUP_ID_` | 紐づく先 | 意味 |
| --- | --- | --- | --- | --- |
| `starter` | `yamada` | | プロセス | 起票者 |
| `candidate` | | `managers` | タスク | **この候補グループが `GET /api/tasks` の絞り込み条件になる** |

`GROUP_ID_` の値（`managers` / `directors`）は BPMN の `candidateGroups` がそのまま入る。
Spring Security のロール文字列と一致させてあるので、IDM テーブルを使わずに突き合わせられる。

## ACT_RU_TIMER_JOB / ACT_RU_JOB — ジョブ（同じ 27 列）

**期限が来ていないもの**が `ACT_RU_TIMER_JOB`、**実行可能になったもの**が `ACT_RU_JOB`。
非同期エグゼキュータが 10 秒ごとに前者を見て、期限切れの行を後者へ移す。

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `TYPE_` | varchar(255) | `timer`（タイマー）／ `async`（非同期 Service Task） |
| `EXECUTION_ID_` / `PROCESS_INSTANCE_ID_` | varchar(64) | 対象の枝・申請 |
| `ELEMENT_ID_` / `ELEMENT_NAME_` | varchar(255) | 発火元の BPMN 要素 |
| `DUEDATE_` | timestamp | **いつ実行するか**。ここを過ぎた行が拾われる |
| `REPEAT_` | varchar(255) | 繰り返し定義。発火のたびに残回数が減る |
| `HANDLER_TYPE_` | varchar(255) | 実行内容の種類 |
| `HANDLER_CFG_` | varchar(4000) | ハンドラへの引数（JSON） |
| `RETRIES_` | integer | 残り試行回数。0 になると `ACT_RU_DEADLETTER_JOB` へ移る |
| `EXCEPTION_MSG_` / `EXCEPTION_STACK_ID_` | varchar / varchar | 失敗時の例外 |
| `LOCK_OWNER_` / `LOCK_EXP_TIME_` | varchar / timestamp | 掴んだノードと占有期限（既定 1 時間）。**多重起動しても二重実行されない仕組み** |
| `EXCLUSIVE_` | boolean | 同一プロセスインスタンスのジョブを直列化するか |
| `CREATE_TIME_` | timestamp | 作成日時 |

リマインドタイマーの実データ。

```
type_        = timer
element_id_  = timerManagerReminder
element_name_= 3日経過リマインド
duedate_     = 2026-08-22 23:02:57.394
repeat_      = R3/2026-08-19T14:02:57.394Z/P3D
handler_type_= trigger-timer
handler_cfg_ = {"activityId":"timerManagerReminder"}
retries_     = 3
exclusive_   = t
```

- `REPEAT_` には**開始時刻が焼き込まれる**。次の期限はこの開始時刻に `P3D` を足していって現在時刻を追い越した最初の時刻になるので、発火が遅れてもズレが蓄積しない
- 発火のたびに `R3` → `R2` → `R1` と減った行が作り直され、使い切ると行ごと消える
- 承認されて親タスクが終わると、未発火の行は削除される

基幹システム連携（`flowable:async="true"` の Service Task）は `TYPE_ = async` で
`ACT_RU_JOB` に直接入る。こちらは `DUEDATE_` が空で、即時実行の対象になる。

## ACT_RU_ACTINST — 進行中の通過記録

`ACT_HI_ACTINST` と同じ 18 列を持つ実行時版。プロセス完了時に削除される。
本アプリはこのテーブルを直接読まない（履歴は `ACT_HI_ACTINST` から取る）。

リマインドを3回発火させた申請では、**境界イベントの行が発火ごとに閉じて開き直る**のが観察できる。

| `ACT_ID_` | `ACT_TYPE_` | `START_TIME_` | `END_TIME_` |
| --- | --- | --- | --- |
| `userTaskManagerApproval` | userTask | 12:49:34.495 | （継続中） |
| `timerManagerReminder` | boundaryEvent | 12:49:34.495 | 12:49:43.427 |
| `serviceTaskManagerReminder` | serviceTask | 12:49:43.428 | 12:49:43.429 |
| `timerManagerReminder` | boundaryEvent | 12:49:43.428 | 12:49:47.712 |
| `serviceTaskManagerReminder` | serviceTask | 12:49:47.712 | 12:49:47.713 |
| `timerManagerReminder` | boundaryEvent | 12:49:47.712 | 12:49:51.977 |
| `serviceTaskManagerReminder` | serviceTask | 12:49:51.977 | 12:49:51.978 |
| `timerManagerReminder` | boundaryEvent | 12:49:51.977 | （継続中） |

親のユーザータスクが閉じていないのが**非中断型**の証拠。

---

# 3. 履歴（完了後に残るもの）

`history-level: audit` で運用している。**外部キー制約は 1 本もない**（書き込み性能を優先した Flowable の設計）。
`PROC_INST_ID_` による論理参照だけがある。

## ACT_HI_PROCINST — 申請1件のサマリ（1申請 1行）

| 列 | 型 | 保存される内容 | 実データ |
| --- | --- | --- | --- |
| `ID_` / `PROC_INST_ID_` | varchar(64) | 申請の識別子（同値） | `775a37c3-…` |
| `PROC_DEF_ID_` | varchar(64) | どの版で動いたか | `expenseApprovalProcess:1:…` |
| `START_TIME_` / `END_TIME_` | timestamp | 開始・終了 | `12:51:46.911` / `12:51:47.794` |
| `DURATION_` | bigint | 所要ミリ秒。**未完了なら NULL** | `883` |
| `START_USER_ID_` | varchar(255) | 起票者 | `yamada` |
| `START_ACT_ID_` | varchar(255) | 開始要素 | `startExpenseRequest` |
| `END_ACT_ID_` | varchar(255) | **どの終了イベントで終わったか** | `endEventApproved` / `endEventRejected` |
| `STATE_` | varchar(255) | `completed` / `externallyTerminated` など | `completed` |
| `END_USER_ID_` | varchar(255) | 終了させたユーザー | – |
| `BUSINESS_KEY_` / `NAME_` / `DELETE_REASON_` / `SUPER_PROCESS_INSTANCE_ID_` / `CALLBACK_*` / `REFERENCE_*` | – | 本アプリでは未使用 | 空 |

**承認されたか却下されたかは `END_ACT_ID_` で判別できる**（`endEventApproved` / `endEventRejected`）。
`END_TIME_` が NULL なら進行中。この1行だけで一覧表示に必要な状態がほぼ揃う。

## ACT_HI_TASKINST — 承認タスクの記録（1タスク 1行）

36 列のうち値が入るもの。

| 列 | 型 | 保存される内容 | 実データ |
| --- | --- | --- | --- |
| `ID_` | varchar(64) | `ACT_RU_TASK.ID_` を引き継ぐ | `775a5ee4-…` |
| `TASK_DEF_KEY_` | varchar(255) | 課長／部長の判別 | `userTaskManagerApproval` |
| `NAME_` | varchar(255) | 表示名 | `課長承認` |
| `STATE_` | varchar(255) | `created` → `completed` | `completed` |
| `ASSIGNEE_` | varchar(255) | **実際に処理した人** | `sato` |
| `CLAIMED_BY_` / `CLAIM_TIME_` | varchar / timestamp | 引き受けた人と時刻 | `sato` |
| `START_TIME_` / `END_TIME_` / `DURATION_` | timestamp / bigint | 発生・完了・滞留ミリ秒 | `862` |
| `FORM_KEY_` | varchar(255) | 入力フォーム | `expenseApprovalForm` |
| `PRIORITY_` | integer | 優先度 | `50` |
| `LAST_UPDATED_TIME_` | timestamp | 最終更新 | – |
| `COMPLETED_BY_` / `DUE_DATE_` / `OWNER_` / `DELETE_REASON_` ほか | – | 本アプリでは未使用 | 空 |

**`DURATION_` が承認の滞留時間**そのものなので、SLA 集計はこの列だけで出せる。

## ACT_HI_ACTINST — 通過した BPMN 要素すべて（1申請あたり 14行〜）

承認履歴 API（`GET /api/expense-requests/{id}/history`）の主データ。

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `ACT_ID_` | varchar(255) | BPMN 要素のID |
| `ACT_NAME_` | varchar(255) | 表示名。`sequenceFlow` には無いことが多い |
| `ACT_TYPE_` | varchar(255) | 要素の種類。**表示の絞り込みに使う** |
| `TASK_ID_` | varchar(64) | `userTask` のときだけ入る。コメント取得のキー |
| `ASSIGNEE_` / `COMPLETED_BY_` | varchar(255) | 担当者・完了者 |
| `START_TIME_` / `END_TIME_` / `DURATION_` | timestamp / bigint | 通過時刻と所要時間 |
| `TRANSACTION_ORDER_` | integer | 同一ミリ秒内の順序 |
| `EXECUTION_ID_` | varchar(64) | どの枝を通ったか |

承認まで進んだ申請の全 14 行。ゲートウェイもシーケンスフローも残る。

| `ACT_ID_` | `ACT_NAME_` | `ACT_TYPE_` | `ASSIGNEE_` | `DURATION_` |
| --- | --- | --- | --- | --- |
| `startExpenseRequest` | 経費精算申請 | startEvent | | 0 |
| `flowStartToAmountGw` | | sequenceFlow | | 0 |
| `gatewayAmountCheck` | 金額判定 | exclusiveGateway | | 0 |
| `flowAmountToManager` | 10万円未満 | sequenceFlow | | 0 |
| `userTaskManagerApproval` | 課長承認 | userTask | `sato` | 864 |
| `timerManagerReminder` | 3日経過リマインド | boundaryEvent | | 864 |
| `flowManagerToJoin` | | sequenceFlow | | 0 |
| `gatewayApprovalJoin` | 承認合流 | exclusiveGateway | | 0 |
| `flowJoinToResult` | | sequenceFlow | | 0 |
| `gatewayApprovalResult` | 承認判定 | exclusiveGateway | | 0 |
| `flowResultToErp` | 承認 | sequenceFlow | | 0 |
| `serviceTaskErpIntegration` | 基幹システム連携 | serviceTask | | 1 |
| `flowErpToEnd` | | sequenceFlow | | 0 |
| `endEventApproved` | 承認完了 | endEvent | | 0 |

人が読む履歴としては細かすぎるので、アプリ側で `ACT_TYPE_` が
`startEvent` / `userTask` / `serviceTask` の行だけに絞っている
（[FlowableApprovalHistoryDatasource.java](../app/src/main/java/cn/gekal/spring/approval/infrastructure/workflow/FlowableApprovalHistoryDatasource.java)）。
境界イベントを除いているのは、この行が**待機期間そのもの**を表していて、発火の事実は
`serviceTaskManagerReminder` 側に出るため二重になるから。

一方で**フロー図の通過経路**（`GET /api/…/diagram`）は `sequenceFlow` の行も要るので、
絞り込まずに全行を使う。同じテーブルを用途で読み分けている。

## ACT_HI_VARINST — 変数の最終値（1申請あたり 11行）

`ACT_RU_VARIABLE` とほぼ同じ列で、`TYPE_` が **`VAR_TYPE_`** に変わる点だけ違う。
加えて `CREATE_TIME_` / `LAST_UPDATED_TIME_` を持つ。

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `NAME_` / `VAR_TYPE_` | varchar | 変数名・型名 |
| `TEXT_` / `LONG_` / `DOUBLE_` / `BYTEARRAY_ID_` | – | 値（`ACT_RU_VARIABLE` と同じ規則） |
| `CREATE_TIME_` / `LAST_UPDATED_TIME_` | timestamp | 初回作成・最終更新 |
| `PROC_INST_ID_` / `EXECUTION_ID_` | varchar(64) | 申請・枝 |

**保持するのは最終値だけ**で、途中経過は残らない。承認完了した申請の全 11 行。

| `NAME_` | `VAR_TYPE_` | `LONG_` | `TEXT_` |
| --- | --- | --- | --- |
| `applicantId` | string | | `yamada` |
| `title` | string | | `タイマー破棄の確認` |
| `amount` | long | `30000` | `30000` |
| `expenseDate` | string | | `2026-08-20` |
| `category` | string | | `会議費` |
| `remarks` | string | | `承認でタイマーが消えるか` |
| `reminderCount` | integer | `0` | `0` |
| `approved` | boolean | `1` | |
| `approvalComment` | string | | `承認` |
| `approverId` | string | | `sato` |
| `erpVoucherNo` | string | | `ERP-20260820-5726` |

多段承認では `approvalComment` / `approverId` が**次の承認者で上書きされる**。
だから各段階のコメントは変数ではなく `ACT_HI_COMMENT` に積んでいる。
値の変遷まで残したい場合は `history-level` を `full` に上げて `ACT_HI_DETAIL` を使うことになるが、
行数が跳ね上がるのでこのアプリでは `audit` のままにしている。

## ACT_HI_COMMENT — 承認コメント（9列）

| 列 | 型 | 保存される内容 |
| --- | --- | --- |
| `ID_` | varchar(64) | – |
| `TYPE_` | varchar(255) | `comment`（人が書いた）/ `event`（エンジンが記録した） |
| `TIME_` | timestamp | 記録時刻 |
| `USER_ID_` | varchar(255) | 書き込んだユーザー |
| `TASK_ID_` | varchar(64) | どのタスクに対してか |
| `PROC_INST_ID_` | varchar(64) | 申請 |
| `ACTION_` | varchar(255) | `AddComment` / `AddUserLink` など |
| `MESSAGE_` | varchar(4000) | 本文（短縮版） |
| `FULL_MSG_` | bytea | 本文（全文）。アプリが読むのはこちら |

**2種類の行が混ざる**ので、読むときは区別が要る。

| `TYPE_` | `ACTION_` | 件数 | 由来 |
| --- | --- | --- | --- |
| `comment` | `AddComment` | 6 | **承認・却下時に人が入力したコメント** |
| `event` | `AddUserLink` | 9 | `claim()` の副作用としてエンジンが自動記録した監査ログ |

アプリは `taskService.getTaskComments(taskId)` を使っており、これは `TYPE_ = 'comment'` だけを返す。
`MESSAGE_` は表示用に切り詰められることがあるため、値は `FULL_MSG_` から取っている。

## ACT_HI_IDENTITYLINK — 関与者の履歴（11列）

`ACT_RU_IDENTITYLINK` に `CREATE_TIME_` が加わったもの（`REV_` はない）。
実行時のリンクに加えて、**完了時のリンクも積み増される**。

| `TYPE_` | `USER_ID_` | `GROUP_ID_` | 紐づく先 |
| --- | --- | --- | --- |
| `starter` | `yamada` | | プロセス |
| `candidate` | | `managers` | タスク |
| `assignee` | `sato` | | タスク |
| `participant` | `sato` | | プロセス |
| `participant` | `sato` | | プロセス |

`participant` が 2 行あるのは、claim 時と complete 時にそれぞれ記録されるため。
**「この申請に誰が関わったか」を人単位で引きたいときの入口**になるテーブルで、
本アプリは今のところ使っていない。

---

# 4. 1申請でできる行の全体像

課長ルート・承認完了までの1申請で、どのテーブルに何行できるか。

| タイミング | 増える行 |
| --- | --- |
| 起票 | `ACT_RU_EXECUTION` 3 / `ACT_RU_TASK` 1 / `ACT_RU_VARIABLE` 7 / `ACT_RU_IDENTITYLINK` 2 / `ACT_RU_TIMER_JOB` 1 / `ACT_HI_PROCINST` 1 / `ACT_HI_TASKINST` 1 / `ACT_HI_ACTINST` 6 |
| リマインド発火（1回ごと） | `ACT_HI_ACTINST` +5（フロー・送信・フロー・終了 と、**張り直された境界イベント 1 行**）／ `ACT_RU_VARIABLE.reminderCount` を更新／ `ACT_RU_TIMER_JOB` は残回数を減らして作り直し |
| 承認 | `ACT_HI_COMMENT` +1 / `ACT_HI_IDENTITYLINK` +3（assignee・participant×2）/ `ACT_RU_VARIABLE` +3（`approved` `approvalComment` `approverId`）/ `ACT_RU_JOB` +1（基幹連携）/ 未発火の `ACT_RU_TIMER_JOB` を削除 |
| 基幹連携 | `ACT_RU_VARIABLE` +1（`erpVoucherNo`） |
| 完了 | **`ACT_RU_*` が全削除** / `ACT_HI_*` は残り、`ACT_HI_ACTINST` が計 14 行、`ACT_HI_VARINST` が 11 行になる |

つまり**完了後に残るのは 1 申請あたり 30 行前後**。`ACT_HI_ACTINST` が最も太り、
`sequenceFlow` とゲートウェイだけで半分を占める。長期運用ではここが増え続けるので、
実案件では Flowable の履歴クリーンアップ（`flowable.history-cleanup`）を検討することになる。

# 5. 作られるが使っていないテーブル

45 テーブルのうち 29 は空のまま。用途を知っておくと「なぜ空なのか」で悩まずに済む。

| テーブル群 | 何のためのものか | なぜ空か |
| --- | --- | --- |
| `ACT_ID_*`（9） | Flowable 内蔵の IDM（ユーザー・グループ・権限） | 承認者は Spring Security のインメモリユーザーで表現している。`ACT_ID_PROPERTY` だけスキーマ版の 1 行が入る |
| `FLW_EVENT_*` / `FLW_CHANNEL_*`（4） | イベントレジストリ（Kafka / JMS 連携） | 外部イベント連携をしていない |
| `FLW_RU_BATCH*`（2） | 一括マイグレーションのバッチ管理 | プロセス定義の移行をしていない |
| `ACT_RU_SUSPENDED_JOB` / `ACT_RU_DEADLETTER_JOB` / `ACT_RU_EXTERNAL_JOB` / `ACT_RU_HISTORY_JOB` | 停止中ジョブ・失敗確定ジョブ・外部ワーカー・非同期履歴 | **失敗が起きていない**（`RETRIES_` が 0 になるとデッドレターに入る） |
| `ACT_RU_EVENT_SUBSCR` | メッセージ／シグナル待ち受け | BPMN にメッセージイベントがない |
| `ACT_RU_ENTITYLINK` / `ACT_HI_ENTITYLINK` | 親子プロセス間の関連 | 呼び出しアクティビティがない |
| `ACT_HI_DETAIL` | 変数の変更履歴 | `history-level: audit` のため記録されない（`full` で入る） |
| `ACT_HI_ATTACHMENT` | タスクへの添付ファイル | 添付機能がない |
| `ACT_HI_TSK_LOG` | タスクのライフサイクルログ | 既定で無効 |
| `ACT_EVT_LOG` | エンジンの全イベントログ | 既定で無効 |
| `ACT_RE_MODEL` / `ACT_PROCDEF_INFO` | Flowable Modeler 用のモデル・定義の動的上書き | Modeler を使っていない |

# 定義の確認方法

このドキュメントは Flowable 8.0.0 時点の実 DB から起こしている。バージョンを上げたら実物で確認する。

```bash
docker exec expense-approval-postgres psql -U flowable -d expense_approval -c "\d+ act_ru_task"
```

```bash
docker exec expense-approval-postgres psql -U flowable -d expense_approval -c "SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 0 ORDER BY n_live_tup DESC;"
```
