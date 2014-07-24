package org.jenkinsci.plugins.zapper;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.listener.BigProjectLogger;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugLogger;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * The Zapper plugin helps to run OWASP ZAP as part of your automated security assessment regime in the Jenkins continuous
 * integration system. The plugin can use a pre-installed version of ZAP when given the path to the ZAP installation.
 * Alternatively, it can automatically download and build a version of ZAP to be used by your security tests.
 *
 * @author Adedayo Adetoye
 */
public class ZapRunner extends Builder {

    private final String host;
    private final ZapInstallDescription zapInstallDescription;
    public static final String AUTO = "auto";

    @DataBoundConstructor
    public ZapRunner(String host, ZapInstallDescription zapInstallDescription) {
        this.host = host;
        this.zapInstallDescription = zapInstallDescription;
    }

    public String getHost() {
        return host;
    }


    public String getZapInstallType() {
        String t = zapInstallDescription == null ? null : zapInstallDescription.getType();
        return t == null ? AUTO : t;
    }

    public ZapInstallDescription getZapInstallDescription() {
        return zapInstallDescription;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        if (AUTO.equals(getZapInstallType())) {

            if (!JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_1_7)) {
                logger.println("Zapper cannot find a suitable JDK." +
                        " You need at least a JDK 7 or above.");
                return false;
            }

            //Checkout ZAP
            new File("zapSource").mkdir();
            File source = new File("zapSource");
            checkout(zapInstallDescription.getRepositoryURL(), source, logger);

            //Build ZAP
            Project project = new Project();
            try {
                project.fireBuildStarted();
                project.init();
                String javaHome = JavaEnvUtils.getJavaHome();
                String JAVA_HOME = javaHome.endsWith("/jre") ?
                        javaHome.substring(0, javaHome.length() - 4) : javaHome;
                project.setProperty("java.home", JAVA_HOME);
                project.setProperty("build.compiler", "extJavac");//important to set this
                project.setProperty("version", "Dev Build");
                File buildFile = new File(source, "build/build.xml");
                ProjectHelper.configureProject(project, buildFile);
                BigProjectLogger projectLogger = new BigProjectLogger();
                projectLogger.setOutputPrintStream(logger);
                project.addBuildListener(projectLogger);
                project.executeTarget("dist");
                project.fireBuildFinished(null);
                //Now run the newly built ZAP
                runZap(source.getAbsolutePath() + "/build/zap", launcher);

            } catch (BuildException e) {
                e.printStackTrace(logger);
                project.fireBuildFinished(e);
                return false;
            }

        } else {
            //Use existing Zap installation
            String path = zapInstallDescription.getPath();
            runZap(path, launcher);
        }
        return true;
    }

    private void runZap(String zapPath, Launcher launcher) throws IOException {
        String zapExecScript = zapPath + "/zap" + (launcher.isUnix() ? ".sh" : ".bat");
        String shell = "/bin/bash";
        if (!launcher.isUnix()) shell = "cmd.exe";
        String[] hostDesc = host.split(":");
        String hostName = hostDesc[0];
        String port = hostDesc.length > 1 ? hostDesc[1] : "8090";
        Launcher.ProcStarter procStarter = launcher.launch().cmdAsSingleString(shell + " " + zapExecScript +
                " -host " + hostName + " -port " + port + " -daemon");
        launcher.launch(procStarter);
    }


    private boolean checkout(String repository, File destination, PrintStream logger) {
        logger.println("About to checkout or update ZAP from " + repository);
        SVNClientManager cm = SVNClientManager.newInstance();
        SVNUpdateClient updateClient = cm.getUpdateClient();
        DefaultSVNDebugLogger svnLogger = new DefaultSVNDebugLogger();
        svnLogger.createLogStream(SVNLogType.DEFAULT, logger);
        updateClient.setDebugLog(svnLogger);
        updateClient.setEventHandler(new SVNEventHandler(logger));

        System.out.println("About to check out OWASP ZAP from " + zapInstallDescription.getRepositoryURL());
        try {
            final long revision = updateClient.doCheckout(SVNURL.parseURIEncoded(zapInstallDescription.getRepositoryURL()),
                    destination, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);
            logger.println("Finished checkout or update. At revision " + revision);
        } catch (SVNException e) {

            logger.println("SVN error:");
            logger.println(e.getErrorMessage());
            SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();
            if (SVNErrorCode.WC_CLEANUP_REQUIRED == errorCode
                    || SVNErrorCode.WC_LOCKED == errorCode
                    || SVNErrorCode.FS_PATH_ALREADY_LOCKED == errorCode) {
                SVNWCClient cleaner = new SVNWCClient(new DefaultSVNAuthenticationManager(destination, false, "", ""), new DefaultSVNOptions());
                cleaner.setEventHandler(new SVNEventHandler(logger));
                cleaner.setDebugLog(svnLogger);
                try {
                    cleaner.doCleanup(destination);
                    return checkout(repository, destination, logger);
                } catch (SVNException e1) {
                    e1.printStackTrace(logger);
                }
            }

            return false;
        }
        return false;
    }

    @Override
    public ZapRunnerDescriptor getDescriptor() {
        return (ZapRunnerDescriptor) super.getDescriptor();
    }

    /**
     * Descriptor for {@link ZapRunner}. Used as a singleton.
     */
    @Extension
    public static final class ZapRunnerDescriptor extends BuildStepDescriptor<Builder> {

        public ZapRunnerDescriptor() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'host'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         *
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        public FormValidation doCheckHost(@QueryParameter("host") String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name such as localhost or localhost:8090");
            if (value.split(":").length > 2)
                return FormValidation.error("Acceptable format include localhost or localhost:8090");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Run OWASP ZAP";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }


        public String getDefaultHost() {
            return "localhost:8090";
        }

        public String getDefaultRepository() {
            return "http://zaproxy.googlecode.com/svn/trunk/";
        }

        public String getDefaultPath() {
            return System.getProperty("user.dir");
        }

    }
}

class SVNEventHandler extends SVNAdminEventAdapter {
    private final PrintStream logger;

    public SVNEventHandler(PrintStream logger) {
        this.logger = logger;
    }

    @Override
    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        super.handleAdminEvent(event, progress);
        logger.println(event.toString());
    }

    @Override
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        super.handleEvent(event, progress);
        logger.println(event.toString());
    }
}