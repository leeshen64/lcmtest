package io.jenkins.plugins.sercomm.openwrt;

import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.sercomm.commons.umei.UMEiError;
import com.sercomm.commons.util.XStringUtil;
import com.sercomm.demeter.microservices.client.v1.PostEchoRequest;
import com.sercomm.demeter.microservices.client.v1.PostEchoResult;
import com.sercomm.demeter.microservices.client.v1.RESTfulClient;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class OpenWRTPlugin extends JobProperty<Job<?, ?>>
{
    public static final String ORIGINATOR_ID = "jenkins-demeter-plugin";

    @Override
    public DescriptorImpl getDescriptor() 
    {
        Jenkins jenkinsInstance = Jenkins.getInstanceOrNull();
        if(null != jenkinsInstance)
        {
            return (DescriptorImpl) jenkinsInstance.getDescriptor(this.getClass());
        }
    
        return null;
    }

    public static DescriptorImpl getDemeterPluginDescriptor() 
    {
        Jenkins jenkinsInstance = Jenkins.getInstanceOrNull();
        if(null != jenkinsInstance)
        {
            return (DescriptorImpl) jenkinsInstance.getDescriptor(OpenWRTPlugin.class);
        }
    
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor 
    {
        private static final String DEFAULT_ENDPOINT = "backend.demeter.smartgaiacloud.com";
        private static final String HELLO_MESSAGE = "Hello Demeter";
        private String endpoint = XStringUtil.BLANK;

        public DescriptorImpl()
        {
            super(OpenWRTPlugin.class);
            this.load();
        }

        @DataBoundConstructor
        public DescriptorImpl(String endpoint) 
        {
            this.endpoint = endpoint;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData)
        throws FormException 
        {
            req.bindParameters(this);

            this.endpoint = formData.getString("endpoint");
            save();

            return super.configure(req, formData);
        }

        @JavaScriptMethod
        public synchronized String defaultEndpoint() 
        {
            return DEFAULT_ENDPOINT;
        }

        public String getEndpoint()
        {
            return this.endpoint;
        }
        
        @Override
        public String getDisplayName() 
        {
            return Messages.DemeterPlugin_DescriptorImpl_DisplayName();
        }
        
        public FormValidation doValidateEndpoint(
                @QueryParameter("endpoint") String value)
        throws IOException, ServletException
        {
            RESTfulClient client = new RESTfulClient.Builder()
                    .enableSSL(true)
                    .endpoint(value).build();
            
            PostEchoRequest request = new PostEchoRequest()
                    .withOriginatorId(ORIGINATOR_ID)
                    .withMessage(HELLO_MESSAGE);
            
            PostEchoResult result = client.postEcho(request);
            if(200 != result.getStatusCode())
            {
                return FormValidation.error("SERVER ACK HTTP " + result.getStatusCode());
            }
            
            if(true == result.hasError())
            {
                UMEiError error = result.getErrors().get(0);
                return FormValidation.error("SERVER RESPONSE HAS ERROR, CODE: " + error.getCode() + ", DETAIL: " + error.getDetail());
            }
            
            return FormValidation.ok("OK");
        }
    }
}
