package org.jenkinsci.plugins.zapper;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Adedayo Adetoye
 */
public class ZapInstallDescription {

    private final String type;
    private final String path;
    private final String repositoryURL;

    @DataBoundConstructor
    public ZapInstallDescription(String value, String path,
                                 String repositoryURL) {
        this.type = value;
        this.path = path;
        this.repositoryURL = repositoryURL;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getRepositoryURL() {
        return repositoryURL;
    }
}
