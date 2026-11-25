import { LegalPage } from "@/components/LegalPage";
import { pageMetadata } from "@/lib/metadata";
import legal from "../../../../legal/content.json";

const { terms } = legal;
const description = "Practical information about using HyperMusic and this website.";

export const metadata = pageMetadata("/terms", terms.title + " | HyperMusic", description);

export default function TermsPage() {
  return <LegalPage document={terms} description={description} />;
}
