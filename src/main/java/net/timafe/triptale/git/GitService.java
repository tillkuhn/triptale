package net.timafe.triptale.git;

import jakarta.annotation.PostConstruct;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.storage.MarkdownStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final TripTaleProperties props;
    private final MarkdownStore store;

    public GitService(TripTaleProperties props, MarkdownStore store) {
        this.props = props;
        this.store = store;
    }

    private static final String GITIGNORE_ENTRY = "prefs.yml";

    @PostConstruct
    public void initOnStartup() {
        Path root = store.dataDir();
        if (!Files.isDirectory(root.resolve(".git"))) {
            try (Git git = Git.init().setDirectory(root.toFile()).call()) {
                log.info("Initialized git repo at {}. Configure 'origin' via 'git remote add' to enable push/pull.", root);
            } catch (GitAPIException e) {
                throw new GitException("Failed to init git repo at " + root, e);
            }
        }
        ensureGitignore(root);
    }

    private void ensureGitignore(Path root) {
        Path gitignore = root.resolve(".gitignore");
        try {
            String existing = Files.exists(gitignore) ? Files.readString(gitignore) : "";
            if (!existing.lines().map(String::trim).anyMatch(GITIGNORE_ENTRY::equals)) {
                String updated = existing.isEmpty()
                        ? GITIGNORE_ENTRY + "\n"
                        : (existing.endsWith("\n") ? existing : existing + "\n") + GITIGNORE_ENTRY + "\n";
                Files.writeString(gitignore, updated);
                log.info("Added '{}' to {}", GITIGNORE_ENTRY, gitignore);
            }
        } catch (IOException e) {
            log.warn("Could not update {}: {}", gitignore, e.getMessage());
        }
    }

    /**
     * Commits all pending changes and returns the abbreviated (short) commit SHA,
     * or {@code null} if there was nothing to commit.
     */
    public String commitAll(String message) {
        Path root = store.dataDir();
        try (Git git = Git.open(root.toFile())) {
            git.add().addFilepattern(".").call();
            if (git.status().call().isClean()) {
                log.debug("Nothing to commit");
                return null;
            }
            PersonIdent author = author();
            var commit = git.commit().setMessage(message);
            if (author != null) commit.setAuthor(author).setCommitter(author);
            RevCommit result = commit.call();
            String shortSha = result.abbreviate(7).name();
            log.info("Committed {}: {}", shortSha, message);
            return shortSha;
        } catch (GitAPIException | IOException e) {
            throw new GitException("Failed to commit", e);
        }
    }

    public String remoteUrl() {
        try (Git git = Git.open(store.dataDir().toFile())) {
            String url = git.getRepository().getConfig().getString("remote", "origin", "url");
            return url == null ? "" : url;
        } catch (IOException e) {
            throw new GitException("Failed to read git config", e);
        }
    }

    public String push() {
        requireOrigin();
        return runGit("push", "origin");
    }

    public String pull() {
        requireOrigin();
        return runGit("pull", "--ff", "origin");
    }

    private void requireOrigin() {
        if (remoteUrl().isBlank()) {
            throw new GitException("No 'origin' remote configured in " + store.dataDir(), null);
        }
    }

    private String runGit(String... args) {
        Path root = store.dataDir();
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new GitException("git " + args[0] + " timed out", null);
            }
            String output = out.toString().trim();
            if (p.exitValue() != 0) {
                throw new GitException("git " + args[0] + " failed: "
                        + (output.isBlank() ? "(no output)" : output), null);
            }
            log.info("git {} ok: {}", args[0], output);
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new GitException("Failed to run git " + args[0], e);
        }
    }

    private PersonIdent author() {
        String name = props.getGit().getAuthorName();
        String email = props.getGit().getAuthorEmail();
        if (name.isBlank() || email.isBlank()) return null;
        return new PersonIdent(name, email);
    }
}
