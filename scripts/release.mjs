import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { basename } from "node:path";

const [command, tag, ...args] = process.argv.slice(2);
assert.match(
  tag ?? "",
  /^v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$/,
  "Invalid release tag",
);
const version = tag.slice(1);
const json = async (file) => JSON.parse(await readFile(file, "utf8"));
const notes = await json(new URL(`../releases/${tag}.json`, import.meta.url));
const headings = {
  features: "Features",
  improvements: "Improvements",
  fixes: "Fixes",
};
const types = {
  features: "feature",
  improvements: "improvement",
  fixes: "fix",
};
const hasText = (value) => typeof value === "string" && value.trim().length > 0;

assert(
  ["stable", "preview"].includes(notes.channel),
  "Choose stable or preview",
);
assert(hasText(notes.summary), "Missing release summary");
const changelog = Object.entries(types).flatMap(([key, type]) => {
  const entries = notes.details?.[key] ?? [];
  assert(Array.isArray(entries) && entries.every(hasText), `Invalid ${key}`);
  return entries.map((text) => ({ type, text }));
});
assert(changelog.length > 0, "Add at least one curated changelog entry");
if (notes.advisory !== undefined) {
  assert(
    notes.advisory &&
      hasText(notes.advisory.title) &&
      hasText(notes.advisory.description),
    "Invalid advisory",
  );
  assert(
    Object.keys(notes.advisory).every((key) =>
      ["title", "description", "badge"].includes(key),
    ),
    "Unknown advisory field",
  );
  assert(
    notes.advisory.badge === undefined || hasText(notes.advisory.badge),
    "Invalid advisory badge",
  );
}

if (command === "notes") {
  const [output] = args;
  const sections = Object.entries(headings).flatMap(([key, heading]) => {
    const entries = notes.details[key] ?? [];
    return entries.length
      ? [`## ${heading}\n\n${entries.map((entry) => `- ${entry}`).join("\n")}`]
      : [];
  });
  if (notes.advisory)
    sections.push(
      `## ${notes.advisory.title}\n\n${notes.advisory.description}`,
    );
  await writeFile(output, [notes.summary, ...sections].join("\n\n") + "\n");
  console.log(notes.channel);
} else if (command === "record") {
  const [
    repository,
    apkPath,
    badgingPath,
    signingPath,
    releasePath,
    feedPath,
    mode,
  ] = args;
  assert.match(repository, /^[\w.-]+\/[\w.-]+$/, "Invalid GitHub repository");
  const [apk, badging, signing, release, feed] = await Promise.all([
    readFile(apkPath),
    readFile(badgingPath, "utf8"),
    readFile(signingPath, "utf8"),
    json(releasePath),
    json(feedPath),
  ]);
  const apkName = `HyperMusic-${version}.apk`;
  const [, code, apkVersion] =
    badging.match(
      /^package: name='com\.hypermusic' versionCode='(\d+)' versionName='([^']+)'/m,
    ) ?? [];
  const versionCode = Number(code);
  assert(
    Number.isSafeInteger(versionCode) &&
      versionCode > 0 &&
      apkVersion === version,
    "APK package or version does not match tag",
  );
  assert.equal(basename(apkPath), apkName);
  const abis = [
    ...(badging.match(/^native-code:[^\r\n]*/m)?.[0].matchAll(/'([^']+)'/g) ??
      []),
  ]
    .map((match) => match[1])
    .sort();
  assert.deepEqual(
    abis,
    ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"],
    "APK must contain all four ABIs",
  );
  const signer = signing
    .match(/^Signer #1 certificate SHA-256 digest: ([a-f\d]{64})\s*$/im)?.[1]
    .toLowerCase();
  assert(
    signer && /^Number of signers: 1\s*$/m.test(signing),
    "Expected one verified APK signer",
  );

  const url = `https://github.com/${repository}/releases/tag/${tag}`;
  const downloadUrl = `https://github.com/${repository}/releases/download/${tag}/${apkName}`;
  assert.equal(release.tag_name, tag);
  assert.equal(release.prerelease, notes.channel === "preview");
  assert.equal(
    release.assets?.length,
    1,
    "Expected exactly one GitHub release asset",
  );
  const [asset] = release.assets;
  const sha256 = createHash("sha256").update(apk).digest("hex");
  assert.equal(asset.name, apkName);
  assert.equal(asset.state, "uploaded");
  assert.equal(asset.size, apk.length);
  assert.equal(
    asset.digest,
    `sha256:${sha256}`,
    "GitHub asset digest differs from verified APK",
  );
  assert(
    feed.schemaVersion === 1 && Array.isArray(feed.releases),
    "Invalid published feed",
  );
  const previous = feed.releases.find((entry) => entry.version === version);
  assert(
    feed.releases.every((entry) => entry.signerSha256 === signer),
    "APK signing certificate changed",
  );
  if (!previous) {
    assert(
      feed.releases.every((entry) => entry.versionCode < versionCode),
      "Version code must increase",
    );
  } else {
    assert(
      previous.versionCode === versionCode && previous.apk.sha256 === sha256,
      "Published APK changed",
    );
  }
  if (mode !== "--check") {
    assert(
      mode === undefined &&
        release.draft === false &&
        Number.isFinite(Date.parse(release.published_at)),
      "Release must be public",
    );
    assert.equal(release.html_url, url);
    assert.equal(asset.browser_download_url, downloadUrl);
    const record = {
      version,
      versionCode,
      date: new Date(release.published_at).toISOString().slice(0, 10),
      channel: notes.channel,
      url,
      summary: notes.summary,
      details: notes.details,
      ...(notes.advisory ? { advisory: notes.advisory } : {}),
      signerSha256: signer,
      apk: { name: apkName, url: downloadUrl, bytes: apk.length, sha256 },
    };
    const releases = [
      record,
      ...feed.releases.filter((entry) => entry.version !== version),
    ].sort((a, b) => b.versionCode - a.versionCode);
    const latest =
      releases.find((entry) => entry.channel === "stable") ?? releases[0];
    const data = {
      latestVersion: latest.version,
      versionCode: latest.versionCode,
      forceUpdate: false,
      releaseDate: latest.date,
      downloadUrl: latest.apk.url,
      changelog:
        latest === record
          ? changelog
          : Object.entries(types).flatMap(([key, type]) =>
              (latest.details[key] ?? []).map((text) => ({ type, text })),
            ),
      ...(latest.advisory ? { advisory: latest.advisory } : {}),
    };
    await writeFile(
      feedPath,
      JSON.stringify(
        { schemaVersion: 1, releases, status: "success", data },
        null,
        2,
      ) + "\n",
    );
  }
} else {
  throw new Error("Expected notes or record");
}
