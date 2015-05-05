package org.jenkinsci.plugins.zapper;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Adedayo Adetoye
 */
public class ZapInstallDescription {

    //Install type ("auto" means automatically download and compile ZAP, otherwise an installed ZAP location is provided)
    private final String type;
    //ZAP install path
    private final String path;
    //ZAP source repository
    private final String repositoryURL;
    //Local path to checkout ZAP source to
    private final String sourcePath;


    @DataBoundConstructor
    public ZapInstallDescription(String value, String path,
                                 String repositoryURL, String sourcePath) {
        this.type = value;
        this.path = path;
        this.repositoryURL = repositoryURL;
        this.sourcePath = sourcePath;
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

    public String getSourcePath() {
        return sourcePath;
    }
}
