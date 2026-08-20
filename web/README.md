# web

経費精算承認サンプルの**動作確認コンソール**。Next.js（App Router）の静的書き出し + MUI で作った開発用 GUI。

配信の入口は2つある。どちらも GUI と API が同一オリジンになるため CORS が要らず、HTTP Basic 認証を
そのまま使える。

| 配信元 | URL | 仕組み |
| --- | --- | --- |
| `web` サービス（compose） | `https://local.gekal.cn` | nginx が静的ファイルを返し、`/api` と `/actuator` を app へ中継する |
| `app` サービス（Spring Boot） | `http://localhost:8080` | `npm run build:app` で `app/src/main/resources/static/` へ入れた成果物を配信する（**生成物なので Git 管理外**。ビルドしていなければ GUI は出ない） |

`web` の土台は `gekal/nginx-local-domains:latest-gekal`。`local.gekal.cn` / `*.local.gekal.cn` の
公的に信頼された証明書を同梱しているため、独自 CA の導入なしに HTTPS で開ける
（`local.gekal.cn` は公開 DNS が `127.0.0.1` を指す）。証明書の有効期限が切れたらイメージを
`docker pull` し直す。

## 前提

- Node.js 22 以上（開発時は 24 で確認）

## セットアップと開発

```bash
npm install

# 0) いちばん手軽: compose で一式起動する（web サービスがビルドまで行う）
cd .. && docker compose up -d --build   # https://local.gekal.cn/

# 1) Spring Boot 単体で GUI ごと動かす
npm run build:app          # out/ を app/src/main/resources/static/ へコピーする
cd .. && ./gradlew app:bootRun   # http://localhost:8080 で GUI ごと起動
# Gradle からまとめて実行する場合: ./gradlew app:buildWeb

# 2) 画面を作り込むとき: Next.js の開発サーバを使う
npm run dev                # http://localhost:3000
# 別ターミナルで API を CORS 許可付きで起動する
cd .. && ./gradlew app:bootRun --args='--app.web-dev-origin=http://localhost:3000'
# GUI 側は API の向き先を渡す: NEXT_PUBLIC_API_BASE=http://localhost:8080 npm run dev
```

## 検証

```bash
npm run typecheck   # TypeScript 7
npm run lint        # ESLint + Prettier
npm test            # node:test（表示整形と承認ルーティングの写し）
npm run build       # out/ への書き出しのみ
npm run preview     # 書き出し結果を単体で表示（API は繋がらない）
```

## 画面でできること

| 区画 | 内容 |
| --- | --- |
| ログイン | 山田（申請者）/ 佐藤（課長）/ 田中（部長）/ admin（フロー定義の運用者）を切り替える。権限で見える区画が変わる |
| 経費精算を申請する | 金額に応じて回付先（課長 / 部長）を事前表示してから申請する |
| 承認タスク | 自分の候補グループ宛てのタスクを承認・却下する（却下はコメント必須） |
| 自分の申請 | 状態・承認者・伝票番号・リマインド回数を一覧する。行から詳細を開く |
| 申請の詳細 | プロセス変数の全項目を表示する |
| 承認フロー図 | BPMN 図を bpmn-js で描画し、通過済み（緑）・実行中（橙）を塗り分ける。ドラッグ・ホイールで拡大縮小、「全画面」ボタンで全画面表示（Esc または閉じるボタンで戻る） |
| 承認履歴 | 誰がいつ何をしたかをタイムラインで表示する（実施者・所要時間・承認コメント付き） |
| 承認フロー定義 | **admin のみ**。版の一覧（状態・配備の由来・走行中の件数）、BPMN の配備、切り戻し、停止・再開。配備した時点から新規の起票が新しい版で始まる（アプリの再起動は不要） |
| API ログ | 直近の API 呼び出しとレスポンス本文をそのまま表示する |

区画の出し分けはクライアント側の決め打ちではなく、`GET /api/me` が返す権限で判断している。
申請者に「承認フロー定義」は出ず、admin に申請フォームと承認タスクは出ない。

画面の更新は**手動**（ヘッダの「再読み込み」）。基幹システム連携とリマインドは非同期ジョブで、
操作直後には結果が入っていないことがあるため、少し待ってから押し直すと反映される。
最終更新時刻をヘッダに出しているので、いつ時点の内容かが分かる。

## ディレクトリ構成

```
src/
  app/                     ルーティング（単一ページ）。layout.tsx / page.tsx / robots.ts
  components/
    atoms/                 status-chip / role-chip
    molecules/             login-form / request-form / api-log-item
    organisms/             approval-console（状態の集約）/ request-table / task-list /
                           request-detail / api-log-panel
    providers/             mui-provider（テーマと Emotion キャッシュ）
  lib/                     api-client / api-types / format / approval-policy（+ テスト）
  config/site.ts           画面名・API ベース URL・サンプルユーザー
```

## 依存を足すときの注意

`package-lock.json` を更新したら、**Linux 側の optional 依存も含まれる形で作り直す**こと。
macOS で `npm install` しただけだと `@emnapi/*` などの Linux 向けパッケージが欠け、
Docker イメージのビルド（`npm ci`）が失敗する。

```bash
docker run --rm -v "$PWD":/w -w /w node:24-bookworm-slim npm install --package-lock-only
npm ci   # 手元でも通ることを確認する
```

## 規約からの逸脱

`nextjs-static-site-conventions` は静的サイトに MUI を入れず Tailwind を使う方針だが、
このコンソールは依頼により **MUI** を使っている。フロー図の描画には bpmn-js を使う
（画面右下の bpmn.io の表記はライセンス上そのままにしておくこと）。あわせて、コンテンツ配信ではなく API の
動作確認が目的のため、**実行時に API を fetch する**（ビルド時取得ではない）。
