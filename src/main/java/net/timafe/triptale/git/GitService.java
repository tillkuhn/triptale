package net.timafe.triptale.git;

import jakarta.annotation.PostConstruct;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.storage.MarkdownStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PushResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final TripTaleProperties props;
    private final MarkdownStore store;

    public GitService(TripTaleProperties props, MarkdownStore store) {
        this.props = props;
        this.store = store;
    }

    @PostConstruct
    public void initOnStartup() {
        Path root = store.dataDir();
        if (!Files.isDirectory(root.resolve(".git"))) {
            try (Git git = Git.init().setDirectory(root.toFile()).call()) {
                log.info("Initialized git repo at {}", root);
                if (!props.getGit().getRemote().isBlank()) {
                    StoredConfig cfg = git.getRepository().getConfig();
                    cfg.setString("remote", "origin", "url", props.getGit().getRemote());
                    cfg.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                    cfg.save();
                }
            } catch (GitAPIException | IOException e) {
                throw new GitException("Failed to init git repo at " + root, e);
            }
        }
    }

    public void commitAll(String message) {
        Path root = store.dataDir();
        try (Git git = Git.open(root.toFile())) {
            git.add().addFilepattern(".").call();
            if (git.status().call().isClean()) {
                log.debug("Nothing to commit");
                return;
            }
            PersonIdent author = author();
            var commit = git.commit().setMessage(message);
            if (author != null) commit.setAuthor(author).setCommitter(author);
            commit.call();
            log.info("Committed: {}", message);
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

    public Iterable<PushResult> push() {
        try (Git git = Git.open(store.dataDir().toFile())) {
            if (originUrl(git).isBlank()) {
                throw new GitException("No 'origin' remote configured in " + store.dataDir(), null);
            }
            return git.push().setRemote("origin").call();
        } catch (GitAPIException | IOException e) {
            throw new GitException("Failed to push", e);
        }
    }

    public PullResult pull() {
        try (Git git = Git.open(store.dataDir().toFile())) {
            if (originUrl(git).isBlank()) {
                throw new GitException("No 'origin' remote configured in " + store.dataDir(), null);
            }
            return git.pull().setRemote("origin").call();
        } catch (GitAPIException | IOException e) {
            throw new GitException("Failed to pull", e);
        }
    }

    private static String originUrl(Git git) {
        String url = git.getRepository().getConfig().getString("remote", "origin", "url");
        return url == null ? "" : url;
    }

    private PersonIdent author() {
        String name = props.getGit().getAuthorName();
        String email = props.getGit().getAuthorEmail();
        if (name.isBlank() || email.isBlank()) return null;
        return new PersonIdent(name, email);
    }
}
