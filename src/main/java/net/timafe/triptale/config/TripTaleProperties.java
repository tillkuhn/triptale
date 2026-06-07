package net.timafe.triptale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "triptale")
public class TripTaleProperties {

    private String dataDir = "~/.triptale";
    private Git git = new Git();

    public Path resolvedDataDir() {
        String expanded = dataDir.startsWith("~")
                ? System.getProperty("user.home") + dataDir.substring(1)
                : dataDir;
        return Paths.get(expanded).toAbsolutePath().normalize();
    }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public Git getGit() { return git; }
    public void setGit(Git git) { this.git = git; }

    public static class Git {
        private String authorName = "";
        private String authorEmail = "";
        private String remote = "";

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
        public String getRemote() { return remote; }
        public void setRemote(String remote) { this.remote = remote; }
    }
}
