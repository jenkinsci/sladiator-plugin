package jenkins.plugins.sladiator;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.IOException;
import java.io.PrintStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

/**
 * SLAdiatorNotifier
 * @author Martins Kemme
 */
public class SLAdiatorNotifier extends Notifier {

    private final String token;
    private final String name;

    @DataBoundConstructor
    public SLAdiatorNotifier(String name, String token) {
        this.name = name;   // Project name
        this.token = token;
    }

    /**
     * Token
     */
    public String getToken() {
        return token;
    }
    
    /**
     * Project name 
     */
    public String getName() {
        return name;
    }
        
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        if (build.getResult() == Result.SUCCESS) {
	    sendNotification(listener, build);
        }
        else if (build.getResult() == Result.UNSTABLE || build.getResult() == Result.FAILURE) {
	    sendNotification(listener, build);
        }
        return true;
    }
    
    
    void sendNotification(BuildListener listener, AbstractBuild<?, ?> build) {
        PrintStream log = listener.getLogger();
        HttpClient client = new HttpClient();
        int statusCode = 0;
        String body = formMessageBody(build).toString();
        
        /* Get server name from global config and 
        default to SLAdiator.ebit.lv, if not specified */
        String server = getDescriptor().serverName();
        if (server == null || server.equals("")) {
            server = "SLAdiator.com";
        }

        String url = "https://" + server + "/api/tickets";
        log.printf("SLAdiator: calling SLAdiator server %s with message %s.%n", url, build.getResult().toString());                
        PostMethod httpMethod = new PostMethod(url);

	try {
            httpMethod.setRequestHeader("Content-Type", "application/json");
            httpMethod.setRequestHeader("SLA_TOKEN", getToken());
            httpMethod.setRequestEntity(new StringRequestEntity(body, "application/json", null));
            statusCode = client.executeMethod(httpMethod);
        } catch (HttpException e) {
                log.printf("SLAdiator: HttpException: %s%n", e.getMessage());
                log.printf(e.getStackTrace().toString());
        } catch (IOException e) {
                log.printf("SLAdiator: IOException: %s%n", e.getMessage());
        } catch (Exception e) {
                log.printf("SLAdiator: Exception: %s%n", e.getMessage());
        } finally {
            httpMethod.releaseConnection();
        }        
        if (statusCode != 200) {
                log.printf("SLAdiator: server response error code was %s%n", statusCode);
        }
    }
    
    public net.sf.json.JSONObject formMessageBody(AbstractBuild<?, ?> build) {
        String jobName = build.getBuildVariables().get("JOB_NAME");
        net.sf.json.JSONObject json = new net.sf.json.JSONObject();
        try {
            json.put("project",getName());
            String buildName = build.toString().substring(0, build.toString().lastIndexOf(build.getDisplayName()) - 1);
            json.put("key", buildName);
            /* For getRootURL to work, it is important, that Jenkins global parameter is configured */
            json.put("url",Hudson.getInstance().getRootUrl() + "/" + build.getUrl());
            json.put("issue_type","build");
            json.put("priority","1");
            json.put("status",build.getResult().toString());
            json.put("issue_created_at",build.getTime().toString());
            json.put("issue_updated_at",build.getTime().toString());
            json.put("resolution",(String)null);
            json.put("source","jenkins-plugin v1.1-SNAPSHOT");
        } catch (net.sf.json.JSONException e) {
        }
        return json;
    }
    
    @Extension
    public static final class DescriptorImpl extends 
        BuildStepDescriptor<Publisher> {
        
        private String serverName;

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Send notifications to SLAdiator monitoring server";
        }
        
        @Override
        public boolean configure(StaplerRequest staplerRequest, net.sf.json.JSONObject json) throws FormException {
            System.out.println("");
            // to persist global configuration information,
            // set that to properties and call save().
            try {
                serverName = json.getString("serverName");
                save();
                return true; // indicate that everything is good so far
            } catch (net.sf.json.JSONException e) {
//                e.getMessage();
                return false;
            }
        }
        
        public String serverName() {
            return serverName;
        }      
    }
        
    @Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl)super.getDescriptor();
    }
    
}