/** 表示整形。桁区切りや日時の切り出しを1箇所に閉じ込める。 */

/** 金額を「50,000 円」の形にする。 */
export function formatYen(amount: number): string {
  return `${amount.toLocaleString('ja-JP')} 円`;
}

/**
 * サーバが返す ISO 形式の日時を「2026-08-19 21:19:24」にする。
 *
 * サーバはタイムゾーンなしの LocalDateTime を返すため、Date へ変換すると
 * ブラウザのタイムゾーンでずれる。文字列のまま切り出す。
 */
export function formatDateTime(value: string | null): string {
  if (value === null) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}
