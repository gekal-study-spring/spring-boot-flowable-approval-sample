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

/**
 * 所要時間を「57.1 秒」「3 分 12 秒」「1 時間 5 分」のように整形する。
 *
 * 承認待ちの滞留時間が一目で分かればよいので、大きい単位では下位の桁を切り捨てる。
 */
export function formatDuration(millis: number | null): string {
  if (millis === null) {
    return '-';
  }
  const seconds = millis / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} 秒`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分 ${Math.floor(seconds % 60)} 秒`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 時間 ${minutes % 60} 分`;
  }
  return `${Math.floor(hours / 24)} 日 ${hours % 24} 時間`;
}
