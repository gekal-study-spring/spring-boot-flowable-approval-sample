import type { Metadata } from 'next';
import { ApprovalConsole } from '@/components/organisms/approval-console';
import { SITE } from '@/config/site';

export const metadata: Metadata = {
  title: SITE.name,
  description: SITE.description,
};

/** コンソールのトップページ。状態を持つのは配下のクライアント部品だけ。 */
export default function Page() {
  return <ApprovalConsole />;
}
