import type { NextConfig } from 'next';

// この GUI は Spring Boot の静的リソース（app/src/main/resources/static）として配信する。
// API と同一オリジンになるので CORS が不要で、HTTP Basic 認証もそのまま使える。
const nextConfig: NextConfig = {
  output: 'export',
  // 静的ホストは / → /index.html を引くため、末尾スラッシュに揃える
  trailingSlash: true,
  // 画像最適化はサーバが要る。静的書き出しでは使えない
  images: { unoptimized: true },
  experimental: {
    // 型検査は TS6 の JS API を使う（tsc は TS7 側にあり Next から見つからないため）
    useTypeScriptCli: false,
  },
};

export default nextConfig;
