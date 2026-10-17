package com.devsurf.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Public release metadata; no GitHub account credentials are included in the app. */
final class GitHubUpdates {
    static final long MAX_APK_BYTES = 128L * 1024 * 1024;
    static final String REPOSITORY = "GianCarlozxc/DevSurfsReleases";
    static final class Release {
        final String version, title, notes, url, sha256;
        final long assetId, size;
        Release(JSONObject data) throws Exception {
            version = data.getString("version"); title = data.optString("title", "DevSurf " + version);
            notes = data.optString("notes"); url = data.getString("url"); sha256 = data.getString("sha256");
            assetId = data.getLong("assetId"); size = data.getLong("size");
            if (!version.matches("\\d{1,9}(?:\\.\\d{1,9}){1,3}") || !sha256.matches("[a-fA-F0-9]{64}") || assetId <= 0 || size <= 0 || size > MAX_APK_BYTES)
                throw new IOException("The release has invalid update information.");
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().startsWith("/" + REPOSITORY + "/releases/download/"))
                throw new IOException("The APK is not from DevSurf's release repository.");
        }
        JSONObject json() throws Exception { return new JSONObject().put("version", version).put("title", title).put("notes", notes).put("url", url).put("sha256", sha256).put("assetId", assetId).put("size", size); }
    }
    static boolean newer(String candidate, String installed) {
        try {
            String[] a = candidate.replaceFirst("^[vV]", "").split("\\."), b = installed.replaceFirst("^[vV]", "").split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                long left = i < a.length ? Long.parseLong(a[i]) : 0, right = i < b.length ? Long.parseLong(b[i]) : 0;
                if (left != right) return left > right;
            }
        } catch (NumberFormatException ignored) {}
        return false;
    }
    static Release latest() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL("https://api.github.com/repos/" + REPOSITORY + "/releases/latest").openConnection();
        JSONObject release;
        try {
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json"); connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); connection.setRequestProperty("User-Agent", "DevSurf/" + BuildConfig.VERSION_NAME);
            int status = connection.getResponseCode();
            if (status == 403 || status == 429) throw new IOException("Update checks are temporarily limited. Try again later.");
            if (status == 404) throw new IOException("No published update is available yet.");
            if (status != 200) throw new IOException("Could not check for updates. Check your connection and try again.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) { if (output.size() + count > 2 * 1024 * 1024) throw new IOException("Update information is too large."); output.write(buffer, 0, count); }
                release = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) throw new IOException("No stable update is published yet.");
        String version = release.getString("tag_name").replaceFirst("^[vV]", "");
        JSONArray assets = release.optJSONArray("assets"); JSONObject apk = null;
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if ("DevSurf.apk".equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))) { apk = asset; break; }
        }
        if (apk == null) throw new IOException("The latest release does not have a DevSurf APK yet.");
        String digest = apk.optString("digest");
        if (!digest.startsWith("sha256:")) throw new IOException("The update has no verification checksum yet. Try again later.");
        return new Release(new JSONObject().put("version", version).put("title", release.optString("name", "DevSurf " + version)).put("notes", release.optString("body"))
            .put("url", apk.getString("browser_download_url")).put("sha256", digest.substring(7)).put("assetId", apk.getLong("id")).put("size", apk.getLong("size")));
    }
}
