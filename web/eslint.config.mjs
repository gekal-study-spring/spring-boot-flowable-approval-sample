// eslint-config-next@16 はフラット設定を直接エクスポートするため、FlatCompat は不要。
// `next lint` は Next 16 で廃止されたので、ESLint CLI を直接使う（eslint .）。
import nextCoreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypeScript from 'eslint-config-next/typescript';
import prettierRecommended from 'eslint-plugin-prettier/recommended';

const eslintConfig = [
  {
    // out/ は書き出し結果。Lint 対象にしない
    ignores: ['node_modules/**', '.next/**', 'out/**', 'public/**'],
  },
  ...nextCoreWebVitals,
  ...nextTypeScript,
  // Prettier との競合を消すため最後に重ねる
  prettierRecommended,
];

export default eslintConfig;
