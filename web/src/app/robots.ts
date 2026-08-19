import type { MetadataRoute } from 'next';

// output: 'export' ではビルド時に固定生成する必要がある
export const dynamic = 'force-static';

/** 開発用コンソールなので検索エンジンには拾わせない。 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: '*', disallow: '/' },
  };
}
