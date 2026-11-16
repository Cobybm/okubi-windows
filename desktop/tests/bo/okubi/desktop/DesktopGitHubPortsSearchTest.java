package bo.okubi.desktop;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class DesktopGitHubPortsSearchTest {
    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    static void throwsError(Checked action, String label) {
        try { action.run(); throw new AssertionError("Allowed invalid input: " + label); }
        catch (IOException | IllegalArgumentException expected) { /* fail closed */ }
    }
    interface Checked { void run() throws IOException; }

    private static String repo(String owner, String name, String url, String description,
                               boolean archived, boolean fork) {
        return "{\"owner\":{\"login\":\"" + owner + "\"},\"name\":\"" + name
                + "\",\"html_url\":\"" + url + "\",\"description\":"
                + description + ",\"language\":\"C++\",\"stargazers_count\":42,"
                + "\"archived\":" + archived + ",\"fork\":" + fork + "}";
    }

    public static void main(String[] args) throws Exception {
        String valid = repo("OpenTTD", "OpenTTD",
                "https://github.com/OpenTTD/OpenTTD",
                "\"Motor de juego abierto\"", false, false);
        String copy = repo("OpenTTD", "OpenTTD",
                "https://github.com/OpenTTD/OpenTTD", "null", false, false);
        String spoofed = repo("Fake", "malware",
                "https://github.com.evil.example/Fake/malware", "\"Falso\"", false, false);
        String wrongOwner = repo("Other", "project",
                "https://github.com/RealOwner/project", "\"Fake path\"", false, false);
        String fork = repo("Someone", "fork",
                "https://github.com/Someone/fork", "\"Copia\"", false, true);
        String old = repo("Old", "archived",
                "https://github.com/Old/archived", "\"Archivo\"", true, false);
        String response = "{\"total_count\":1500,\"incomplete_results\":true,\"items\":["
                + String.join(",", valid, copy, spoofed, wrongOwner, fork, old) + "]}";
        DesktopGitHubPortsSearch.SearchPage result =
                DesktopGitHubPortsSearch.parse(response.getBytes(StandardCharsets.UTF_8), 1);
        check(result.candidates().size() == 1, "Exclude duplicates, forks, archived, spoof URLs");
        check(result.candidates().get(0).fullName().equals("OpenTTD/OpenTTD"),
                "Keep verified owner + repository name");
        check(result.candidates().get(0).stars() == 42, "Stars parsed");
        check(result.total() == 1500, "Reported GitHub result count");
        check(result.incomplete(), "Incomplete results clearly marked");
        check(result.hasNext(), "First page has next");
        check(result.candidates().get(0).releases().equals(
                URI.create("https://github.com/OpenTTD/OpenTTD/releases")),
                "Releases link must be official GitHub");
        check(!new DesktopGitHubPortsSearch.SearchPage(
                result.candidates(), 2000, 25, true).hasNext(),
                "GitHub 1000-result cap per query");
        check(!new DesktopGitHubPortsSearch.SearchPage(
                result.candidates(), 39, 1, false).hasNext(),
                "No second page for less than PAGE_SIZE results");
        URI url = DesktopGitHubPortsSearch.endpoint(
                DesktopGitHubPortsSearch.Scope.SOURCE_PORTS, "doom port", 2);
        check("https".equals(url.getScheme())
                && "api.github.com".equals(url.getHost()), "API origin pinned");
        check(url.getRawQuery().contains("page=2") && url.getRawQuery().contains("per_page=40"),
                "Pagination included");
        check(url.getRawQuery().contains("archived%3Afalse"), "No archived results");
        check(url.getRawQuery().contains("fork%3Afalse"), "No forks");
        URI malicious = DesktopGitHubPortsSearch.endpoint(
                DesktopGitHubPortsSearch.Scope.SOURCE_PORTS,
                "https://evil.test/file?token=hi", 1);
        check("api.github.com".equals(malicious.getHost()),
                "Free-text searches can never change API host");
        for (DesktopGitHubPortsSearch.Scope scope : DesktopGitHubPortsSearch.Scope.values()) {
            check(scope.label != null && scope.query != null, "Every scope usable");
            check("api.github.com".equals(DesktopGitHubPortsSearch.endpoint(
                    scope, "", 1).getHost()), "All scopes use official API");
        }
        throwsError(() -> DesktopGitHubPortsSearch.endpoint(
                DesktopGitHubPortsSearch.Scope.RETRO, "", 26), "1000-result cap");
        throwsError(() -> DesktopGitHubPortsSearch.endpoint(
                DesktopGitHubPortsSearch.Scope.RETRO, "", 0), "No zero page");
        throwsError(() -> DesktopGitHubPortsSearch.endpoint(
                DesktopGitHubPortsSearch.Scope.RETRO, "X".repeat(81), 1), "Search length cap");
        throwsError(() -> DesktopGitHubPortsSearch.parse(
                "{\"items\":{}}".getBytes(StandardCharsets.UTF_8), 1), "Invalid list rejected");
        throwsError(() -> DesktopGitHubPortsSearch.parse(
                "{invalid}".getBytes(StandardCharsets.UTF_8), 1), "Invalid JSON rejected");
        throwsError(() -> DesktopGitHubPortsSearch.parse(
                new byte[2_000_001], 1), "Oversized JSON rejected");
        throwsError(() -> new DesktopGitHubPortsSearch.Candidate(
                "OpenTTD", "OpenTTD", "Test", "C++", 1,
                URI.create("https://github.com@evil.example/OpenTTD/OpenTTD")),
                "No URL spoofing");
        throwsError(() -> new DesktopGitHubPortsSearch.Candidate(
                "Bad/Owner", "Repo", "Test", "Java", 1,
                URI.create("https://github.com/Bad/Owner/Repo")),
                "No owner path injection");
        System.out.println("DesktopGitHubPortsSearchTest: PASS");
    }
}
