export interface ReleaseAsset {
  name: string;
  url: string;
  bytes: number;
  sha256: string;
}

export interface ReleaseAdvisory {
  title: string;
  description: string;
  badge?: string;
}

export interface ReleaseDetails {
  features?: string[];
  improvements?: string[];
  fixes?: string[];
}

export interface ReleaseData {
  version: string;
  versionCode: number;
  date: string;
  channel: "stable" | "preview";
  summary: string;
  apk: ReleaseAsset;
  url: string;
  advisory?: ReleaseAdvisory;
  details: ReleaseDetails;
}
