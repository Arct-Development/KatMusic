/**
 * KatMusic release catalogue.
 *
 * Reads the public GitHub releases API for the app repository and exposes the
 * installable APKs. Runs in the browser on purpose: the API allows CORS, so the
 * site stays a plain static page and never needs a token or a server.
 */

export const REPO = "ItzFallenMe/KatMusic";
export const RELEASES_PAGE = `https://github.com/${REPO}/releases`;
export const REPO_PAGE = `https://github.com/${REPO}`;

export type ReleaseAsset = {
  name: string;
  size: number;
  downloaded: number;
  downloadUrl: string;
};

export type Release = {
  tag: string;
  name: string;
  publishedAt: string;
  notes: string;
  htmlUrl: string;
  prerelease: boolean;
  assets: ReleaseAsset[];
  apkCount: number;
};

type RawAsset = {
  name?: string;
  size?: number;
  download_count?: number;
  browser_download_url?: string;
};

type RawRelease = {
  tag_name?: string;
  name?: string;
  published_at?: string;
  body?: string;
  html_url?: string;
  prerelease?: boolean;
  draft?: boolean;
  assets?: RawAsset[];
};

/** APKs first, then the checksum file, then anything else. */
function assetRank(name: string): number {
  const lower = name.toLowerCase();
  if (lower.endsWith(".apk")) {
    if (lower.includes("universal-uncompressed")) return 1;
    if (lower.includes("android7")) return 2;
    return 0;
  }
  if (lower.includes("sha256")) return 3;
  return 4;
}

export function isApk(name: string): boolean {
  return name.toLowerCase().endsWith(".apk");
}

export function formatBytes(bytes: number): string {
  if (!bytes) return "—";
  const mb = bytes / (1024 * 1024);
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

/** Which package to recommend for a given Android version. */
export function recommendFor(assetName: string): string {
  const lower = assetName.toLowerCase();
  if (lower.includes("android7")) return "Android 7 – 9";
  if (lower.includes("uncompressed")) return "Debug-style raw build";
  return "Android 10+";
}

export async function fetchReleases(limit = 12): Promise<Release[]> {
  const response = await fetch(
    `https://api.github.com/repos/${REPO}/releases?per_page=${limit}`,
    { headers: { Accept: "application/vnd.github+json" } },
  );

  if (response.status === 403) {
    throw new Error("GitHub is rate limiting the release lookup. Try again in a minute.");
  }
  if (!response.ok) {
    throw new Error(`Could not load releases (HTTP ${response.status}).`);
  }

  const raw = (await response.json()) as RawRelease[];

  return raw
    .filter((entry) => !entry.draft)
    .map((entry) => {
      const assets = (entry.assets ?? [])
        .filter((asset) => asset.name && asset.browser_download_url)
        .map((asset) => ({
          name: asset.name as string,
          size: asset.size ?? 0,
          downloaded: asset.download_count ?? 0,
          downloadUrl: asset.browser_download_url as string,
        }))
        .sort((a, b) => assetRank(a.name) - assetRank(b.name) || a.name.localeCompare(b.name));

      return {
        tag: entry.tag_name ?? "",
        name: entry.name ?? entry.tag_name ?? "Release",
        publishedAt: entry.published_at ?? "",
        notes: entry.body ?? "",
        htmlUrl: entry.html_url ?? RELEASES_PAGE,
        prerelease: entry.prerelease ?? false,
        assets,
        apkCount: assets.filter((asset) => isApk(asset.name)).length,
      };
    })
    .filter((release) => release.assets.length > 0);
}
