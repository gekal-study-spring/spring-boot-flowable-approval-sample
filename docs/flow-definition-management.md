# フロー定義の運用（再起動なしの差し替え）

承認フローの BPMN をアプリの外で管理し、**アプリを再起動せずに差し替える**ための仕組みと、その限界をまとめる。

## 何ができて、何ができないか

BPMN はアプリ内の Spring Bean を参照しているため、「フロー定義を完全にアプリの外へ出す」ことはできない。
現行の定義がアプリ側に要求しているものは以下の5種類。

```
${erpIntegrationDelegate}   ${rejectNotificationDelegate}   ${reminderNotificationDelegate}
${expenseApprovalPolicy.requiresDirectorApproval(amount)}
formKey="expenseApprovalForm"     candidateGroups="managers" / "directors"
```

これを踏まえると、無停止で変えられる範囲はこうなる。

| 再起動なしで変えられる | アプリのデプロイが要る |
| --- | --- |
| 承認段階の増減・順序の入れ替え | 新しい delegate を参照する（Bean が jar の中にあるため） |
| タイマーの間隔・回数（`R3/P3D` → `R5/P1D`） | 新しい `candidateGroups`（Spring Security 側にロールが要る） |
| 分岐条件の式 | 新しい `formKey`（画面側の対応が要る） |
| 既存 delegate の `field` 値（`targetGroup`・`templateCode`） | アプリが読む新しいプロセス変数の追加 |
| タスク名・要素名・図のレイアウト | |

**「フローの形を変える」のは無停止でできるが、「新しい自動処理を足す」のはできない。**

なお分岐の閾値を BPMN 側に直接書く場合は、[`ExpenseApprovalPolicy`](../app/src/main/java/cn/gekal/spring/approval/domain/service/ExpenseApprovalPolicy.java)
の `approverRoleOf`（画面表示に同じ閾値を使っている）と食い違わないように揃える必要がある。

## なぜ再起動が要らないのか

アプリは `runtimeService.startProcessInstanceByKey()` で起票している。
Flowable の `DeploymentManager.findDeployedLatestProcessDefinitionByKey` は**毎回 DB に問い合わせて最新版を引き直す**ため、
配備した時点から新規の起票が新しい版で始まる。

- 新しい配備は新しい定義ID（`expenseApprovalProcess:2:xxxx`）になるので、**定義キャッシュの無効化は不要**
- 複数ノードで動かしていても、全ノードが次の起票から新版を使う
- **走行中の申請は起票時の版のまま最後まで走る**（`ACT_RU_EXECUTION.PROC_DEF_ID_` が起票時の版を指すため）

## 起動時オートデプロイを切ってある

`application.yaml` で `flowable.check-process-definitions: false` にしている。**これは必須の設定**。

Flowable の起動時オートデプロイ（`DefaultAutoDeploymentStrategy`）は
`enableDuplicateFiltering()` と `name("SpringBootAutoDeployment")` を使う。
重複判定は**同じデプロイ名との比較**なので、管理APIから別名で入れた新しい版とは照合されない。
結果、**起動のたびに jar 内の古い BPMN が「さらに新しい版」として入り直し、フローが巻き戻る**。

代わりに [`PackagedProcessDefinitionBootstrap`](../app/src/main/java/cn/gekal/spring/approval/infrastructure/workflow/PackagedProcessDefinitionBootstrap.java)
が「1版も無ければ同梱の BPMN を配備する」だけを行う。空の DB（初回起動・テスト）では従来どおり動き、
運用中の環境では何もしない。

```
プロセス定義 expenseApprovalProcess は配備済みのため、同梱の BPMN は配備しない（更新は管理APIから行う）
```

## BPMN ファイルの置き場所

原本は今までどおり `app/src/main/resources/processes/expense-approval.bpmn20.xml` に置く。
リポジトリでレビューでき、初回起動の初期値にもなる。**反映は明示的な配備操作で行う**という点だけが変わる。

## 管理画面

動作確認コンソール（`web/`）に **admin でログインしたときだけ**「承認フロー定義」の区画が出る。
出し分けはクライアント側の決め打ちではなく、`GET /api/me` が返す権限で判断している
（サーバの認可設定と同じ情報を使う）。

画面は2段に分かれている。プロセスが複数あると版が混ざって読めないため、
**上段でフロー定義を1行ずつ**見せ、選んだ定義の**配備履歴**だけを下段に出す。

できること:

- **フロー定義の一覧**（上段）— プロセス名・定義キー・最新版・状態・走行中の合計・版数・最終配備日時
- **配備履歴**（下段）— 選んだ定義の版を新しい順に。状態（最新／旧版／停止中）・配備の由来・配備日時・**走行中の件数**
- **BPMN を配備する** — ファイルを選ぶとその場で配備。以後の起票が新しい版で始まる
- **図を見る** — その版の BPMN をそのまま描画（進捗の色付けはしない）
- **この版に戻す** — 切り戻し。最新版では押せない
- **停止 / 再開** — その版での新規の起票だけを止める

admin は `applicants` も承認者グループも持たないため、申請フォームと承認タスクの区画は出ない。
逆に申請者・承認者には「承認フロー定義」の区画が出ない。

## API

`/api/admin/**` は専用ロール `administrators` にだけ開放している。フローの差し替えは業務の流れそのものを
変える操作なので、承認者ロール（`managers` / `directors`）とは分けてある。開発用ユーザーは `admin` / `password`。

| メソッド | パス | 説明 |
| --- | --- | --- |
| GET | `/api/admin/process-definitions` | 版の一覧（新しい順）。走行中の件数つき |
| POST | `/api/admin/process-definitions` | BPMN をアップロードして新しい版として配備（201） |
| GET | `/api/admin/process-definitions/{id}/bpmn` | 指定した版の BPMN XML。差し替え前の控えに使う |
| POST | `/api/admin/process-definitions/{id}/rollback` | 指定した版の内容で配備し直す（201） |
| POST | `/api/admin/process-definitions/{id}/suspend` | その版で新規に起票できないようにする（204） |
| POST | `/api/admin/process-definitions/{id}/activate` | 停止した版を再開する（204） |
| GET | `/api/me` | ログイン中のユーザーと権限（画面の出し分けに使う。全ユーザー可） |

リクエスト例は `apis.rest` の 18〜23 番を参照。

```bash
curl -u admin:password -X POST http://localhost:18080/api/admin/process-definitions -F "file=@expense-approval.bpmn20.xml"
```

### 切り戻しは「古い版で新しい版を作る」

Flowable は版を消さずに積み上げる方式なので、`rollback` も古い版を復活させるのではなく、
**その内容で新しい版を配備する**。履歴が消えないぶん、戻した事実も追える。

```
v3  ApiRollback(admin -> expenseApprovalProcess:1:da915b86-...)   latest=true   走行中=1
v2  ApiDeployment(admin)                                          latest=false  走行中=1
v1  SpringBootAutoDeployment                                      latest=false  走行中=5
```

`deploymentName` に誰がどう入れたかが残るので、一覧を見れば経緯が追える。

## 安全策

- **配備の時点で検証される。** `deploy()` は XSD 検証と BPMN 検証を通す（`disableBpmnValidation()` が
  別に用意されている＝既定は有効）。壊れた定義は 400 で弾かれ、既存の版は差し替わらない。
  起票時に落ちることはない
- **ファイル名は `.bpmn20.xml` か `.bpmn` で終わる必要がある。** Flowable はこの接尾辞でプロセス定義かどうかを
  判定するため、違う名前だと単なる添付ファイルとして格納され、定義が生まれない。API 側で先に弾いている
- **走行中の申請は保護される。** 差し替えても起票時の版で完了する。版の一覧に出る「走行中の件数」で、
  古い版がいつ空になるか見える
- **suspend で新規受付だけ止められる。** 走行中の申請には影響しない

## 実際の動き

compose 上で確認した記録（アプリの再起動なし、`RestartCount = 0`）。

| 手順 | 結果 |
| --- | --- |
| 差し替え前に起票 | `currentTaskName = 課長承認`（v1） |
| 課長承認→課長確認、`R3/P3D`→`R5/P1D` に書き換えて配備 | `version = 2`, `latest = true` |
| 差し替え後に起票 | `currentTaskName = 課長確認`（v2） |
| 差し替え前の申請を再確認 | `currentTaskName = 課長承認`（v1 のまま） |

DB でも版とタイマーが分かれていることを確認できる。

```
    申請    | 版 | タスク名 |            タイマー
------------+----+----------+---------------------------------
 差し替え前 |  1 | 課長承認 | R3/2026-08-20T13:41:57.315Z/P3D
 差し替え後 |  2 | 課長確認 | R5/2026-08-20T13:42:10.514Z/P1D
```

自動テストは [`ProcessDefinitionDeploymentTest`](../app/src/test/java/cn/gekal/spring/approval/workflow/ProcessDefinitionDeploymentTest.java) にある。

## 運用上の注意

- **版は増え続ける。** `ACT_RE_DEPLOYMENT` / `ACT_RE_PROCDEF` / `ACT_GE_BYTEARRAY` に行が積まれる。
  走行中の申請が無くなった古い版は `repositoryService.deleteDeployment()` で消せるが、
  履歴（`ACT_HI_*`）が定義を参照しているため、**履歴を残す限り消さないのが安全**
- **BPMN の `process id`（＝定義キー）は変えない。** 変えると別のフローとして扱われ、
  アプリの `ProcessVariables.PROCESS_DEFINITION_KEY` と一致しなくなって起票できなくなる
- **プロセス変数名は BPMN とアプリの契約。** [`ProcessVariables`](../app/src/main/java/cn/gekal/spring/approval/infrastructure/workflow/ProcessVariables.java)
  に集約してあるので、BPMN 側だけ変えると実行時まで気付けない
