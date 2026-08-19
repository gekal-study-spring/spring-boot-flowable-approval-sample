/**
 * コンソール全体の定義。
 *
 * ブラウザへ配信される設定なので、秘密情報は置かないこと。
 */
export const SITE = {
  name: '経費精算承認 動作確認コンソール',
  description:
    'Flowable の経費精算承認プロセス（課長/部長の振り分け、却下通知、3日リマインド、基幹システム連携）を画面から確認するための開発用 GUI。',
  locale: 'ja_JP',
} as const;

/**
 * API のベース URL。
 *
 * 既定は空文字（同一オリジン）。Spring Boot の静的リソースとして配信するため、
 * ビルド成果物ではこれで足りる。`next dev` で開発するときだけ
 * `NEXT_PUBLIC_API_BASE=http://localhost:8080` を渡してサーバを別オリジンにする。
 */
export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? '';

/** サンプルに用意しているユーザー。権限は BPMN の candidateGroups と一致させてある。 */
export const SAMPLE_USERS = [
  { username: 'yamada', label: '山田（申請者）', groups: 'applicants' },
  { username: 'sato', label: '佐藤（課長）', groups: 'applicants, managers' },
  { username: 'tanaka', label: '田中（部長）', groups: 'applicants, directors' },
] as const;

/** サンプルユーザー共通のパスワード。 */
export const SAMPLE_PASSWORD = 'password';
