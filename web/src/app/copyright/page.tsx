import { LegalPage } from "@/components/LegalPage";
import { pageMetadata } from "@/lib/metadata";
import legal from "../../../../legal/content.json";

const { copyright } = legal;
const description = "Project attribution, licenses, and reporting concerns about content.";

export const metadata = pageMetadata("/copyright", copyright.title + " | HyperMusic", description);

export default function CopyrightPage() {
  return <LegalPage document={copyright} description={description} />;
}
