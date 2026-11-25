import { LegalPage } from "@/components/LegalPage";
import { pageMetadata } from "@/lib/metadata";
import legal from "../../../../legal/content.json";

const { privacy } = legal;
const description = "How HyperMusic and this website handle information, storage, and external services.";

export const metadata = pageMetadata("/privacy", privacy.title + " | HyperMusic", description);

export default function PrivacyPage() {
  return <LegalPage document={privacy} description={description} />;
}
