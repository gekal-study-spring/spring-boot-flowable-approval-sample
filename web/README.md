# web

経費精算承認サンプルの**動作確認コンソール**。Next.js（App Router）の静的書き出し + MUI で作った開発用 GUI。

ビルド成果物は `app/src/main/resources/static/` へ出力し、Spring Boot が静的リソースとして配信する。
API と同一オリジンになるため CORS が不要で、HTTP Basic 認証をそのまま使える。

## 前提

- Node.js 22 以上（開発時は 24 で確認）

## セットアップと開発

```bash
npm install

# 1) 通常の確認: ビルドして Spring Boot から配信する（推奨）
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
| ログイン | 山田（申請者）/ 佐藤（課長）/ 田中（部長）を切り替える。権限で見える情報が変わる |
| 経費精算を申請する | 金額に応じて回付先（課長 / 部長）を事前表示してから申請する |
| 承認タスク | 自分の候補グループ宛てのタスクを承認・却下する（却下はコメント必須） |
| 自分の申請 | 状態・承認者・伝票番号・リマインド回数を一覧する。行から詳細を開く |
| 申請の詳細 | プロセス変数の全項目を表示する |
| API ログ | 直近の API 呼び出しとレスポンス本文をそのまま表示する |

自動更新（3秒）を既定で有効にしている。基幹システム連携とリマインドは非同期ジョブなので、
操作直後ではなく数秒後に結果が反映されるため。

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

## 規約からの逸脱

`nextjs-static-site-conventions` は静的サイトに MUI を入れず Tailwind を使う方針だが、
このコンソールは依頼により **MUI** を使っている。あわせて、コンテンツ配信ではなく API の
動作確認が目的のため、**実行時に API を fetch する**（ビルド時取得ではない）。
