package io.jenkins.plugins.sercomm.openwrt;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.sercomm.commons.id.NameRule;
import com.sercomm.commons.umei.UMEiError;
import com.sercomm.commons.util.DateTime;
import com.sercomm.commons.util.XStringUtil;
import com.sercomm.demeter.microservices.client.v1.GetDeviceRequest;
import com.sercomm.demeter.microservices.client.v1.GetDeviceResult;
import com.sercomm.demeter.microservices.client.v1.GetDevicesRequest;
import com.sercomm.demeter.microservices.client.v1.GetDevicesResult;
import com.sercomm.demeter.microservices.client.v1.GetInstallableAppsRequest;
import com.sercomm.demeter.microservices.client.v1.GetInstallableAppsResult;
import com.sercomm.demeter.microservices.client.v1.GetInstalledAppRequest;
import com.sercomm.demeter.microservices.client.v1.GetInstalledAppResult;
import com.sercomm.demeter.microservices.client.v1.PostUbusCommandRequest;
import com.sercomm.demeter.microservices.client.v1.PostUbusCommandResult;
import com.sercomm.demeter.microservices.client.v1.RESTfulClient;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;

public class OpenWRTCollectBuilder extends Builder implements SimpleBuildStep
{
    private String deviceId;

    private String appPublisher;
    private String appName;
    private String appVersion;   
    private String duration;
    private String frequency;

    @DataBoundConstructor
    public OpenWRTCollectBuilder(
            String deviceId,
            String appPublisher,
            String appName,
            String appVersion,
            String duration,
            String frequency) 
    {
        this.deviceId = deviceId;
        
        this.appPublisher = appPublisher;
        this.appName = appName;
        this.appVersion = appVersion;
        this.duration = duration;
        this.frequency = frequency;
    }

    public String getDeviceId() 
    {
        return deviceId;
    }

    public String getAppPublisher()
    {
        return appPublisher;
    }

    public String getAppName()
    {
        return appName;
    }
    
    public String getAppVersion()
    {
        return appVersion;
    }
    
    public String getDuration()
    {
        return duration;
    }

    public String getFrequency()
    {
        return frequency;
    }
    
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) 
    throws InterruptedException, IOException 
    {
        final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
        final PrintStream logger = listener.getLogger();

        // convert to millisecnods
        final long durationValue = Long.parseLong(this.duration) * 1000L;
        final long frequencyValue = Long.parseLong(this.frequency) * 1000L;

        logger.println(LogParserUtil.SYMBOL_COLLECT_PROCEDURE_BPOS);
        logger.printf("%s - [INFO] ====== Collection Builder ======%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
        logger.println(LogParserUtil.SYMBOL_DESCRIBE_BPOS);
        logger.printf("%s - [INFO] App publisher: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), this.appPublisher);
        logger.printf("%s - [INFO] App name: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), this.appName);
        logger.printf("%s - [INFO] App version: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), this.appVersion);
        logger.printf("%s - [INFO] Duration: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), this.duration);
        logger.printf("%s - [INFO] Frequency: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), this.frequency);
        logger.printf("%s - [INFO] Endpoint: %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), endpoint);
        logger.println(LogParserUtil.SYMBOL_DESCRIBE_EPOS);
        
        try
        {
            RESTfulClient client = new RESTfulClient.Builder()
                    .enableSSL(true)
                    .endpoint(endpoint).build();
            
            // 1. check device status and its model name
            logger.printf("%s - [INFO] checking device status... ", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
            GetDeviceRequest getDeviceRequest = new GetDeviceRequest()
                    .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                    .withNodeName(this.deviceId);

            GetDeviceResult getDeviceResult = client.getDevice(getDeviceRequest);
            if(200 != getDeviceResult.getStatusCode())
            {
                logger.printf("failed%n");
                throw new InterruptedException("SERVER HTTP " + getDeviceResult.getStatusCode() + ", METHOD: 'getDevice'");
            }

            if(getDeviceResult.hasError())
            {
                logger.printf("failed%n");
                
                UMEiError error = getDeviceResult.getErrors().get(0);
                throw new InterruptedException("SERVER REPORTED ERROR,  CODE: " +  error.getCode() + ", DETAIL: " + error.getDetail());
            }
            
            if(0 != getDeviceResult.getData().getState().compareTo("online"))
            {
                logger.printf("failed%n");
                throw new InterruptedException("DEVICE IS NOT ONLINE");
            }
            logger.printf("ok%n");

            // 2. obtaining App list
            logger.printf("%s - [INFO] obtaining App list... ", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
            GetInstallableAppsRequest getInstallableAppRequest = new GetInstallableAppsRequest()
                    .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                    .withModel(getDeviceResult.getData().getModel())
                    .withFrom(0)
                    .withSize(500);

            GetInstallableAppsResult getInstallableAppResult = client.getInstallableApps(getInstallableAppRequest);
            if(200 != getInstallableAppResult.getStatusCode())
            {
                logger.printf("failed%n");
                throw new InterruptedException("SERVER HTTP " + getInstallableAppResult.getStatusCode() + ", METHOD: 'getInstallableApps'");
            }

            if(getInstallableAppResult.hasError())
            {
                logger.printf("failed%n");

                UMEiError error = getInstallableAppResult.getErrors().get(0);
                throw new InterruptedException("SERVER REPORTED ERROR,  CODE: " +  error.getCode() + ", DETAIL: " + error.getDetail());
            }                        
            logger.printf("ok%n");
            
            // 2-1. checking the specific App to be available or not
            logger.printf("%s - [INFO] checking specific App... ", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
            GetInstallableAppsResult.ResultData installableApp = null;
            GetInstallableAppsResult.ResultData.Version installableVersion = null;
            
            for(GetInstallableAppsResult.ResultData app : getInstallableAppResult.getData())
            {
                if(0 != app.getPublisher().compareTo(this.appPublisher))
                {
                    continue;
                }
                
                if(0 != app.getAppName().compareTo(this.appName))
                {
                    continue;
                }
                
                for(GetInstallableAppsResult.ResultData.Version version : app.getVersions())
                {
                    if(0 != version.getVersionName().compareTo(this.appVersion))
                    {
                        continue;
                    }
                    
                    installableApp = app;
                    installableVersion = version;
                }
            }
            
            if(null == installableApp || null == installableVersion)
            {
                logger.printf("failed%n");
                
                throw new InterruptedException("SPECIFIC APP CANNOT BE FOUND");
            }
            logger.printf("ok%n");
            
            // 3. check if device has installed the specific App
            logger.printf("%s - [INFO] checking device installed Apps... ", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
            GetInstalledAppRequest getInstalledAppRequest = new GetInstalledAppRequest()
                    .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                    .withNodeName(this.deviceId)
                    .withAppId(installableApp.getAppId());
            GetInstalledAppResult getInstalledAppResult = client.getInstalledApp(getInstalledAppRequest);
            if(200 != getInstalledAppResult.getStatusCode())
            {
                logger.printf("failed%n");
                throw new InterruptedException("SERVER HTTP " + getInstalledAppResult.getStatusCode() + ", METHOD: 'getInstalledApp'");
            }
            logger.printf("ok%n");

            if(true == getInstalledAppResult.hasError())
            {
                throw new InterruptedException("SPECIFIC APP HAS NOT BEEN INSTALLED YET");
            }
            
            logger.println(LogParserUtil.SYMBOL_DETAIL_BPOS);

            final long beginTime = System.currentTimeMillis();
            while(System.currentTimeMillis() - beginTime <= durationValue)
            {
                Thread.sleep(frequencyValue);

                PostUbusCommandRequest request = new PostUbusCommandRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withNodeName(this.deviceId)
                        .withRequestContents("List", "Services.Management.LCM.ExecutionEnvironments", XStringUtil.BLANK);
                
                PostUbusCommandResult result = client.postUbusCommand(request);
                if(200 != result.getStatusCode())
                {
                    throw new InterruptedException("SERVER HTTP " + getInstalledAppResult.getStatusCode() + ", METHOD: 'getInstalledApp'");
                }
                
                if(true == result.hasError())
                {
                    UMEiError error = result.getErrors().get(0);
                    throw new InterruptedException("SERVER REPORTED ERROR,  CODE: " +  error.getCode() + ", DETAIL: " + error.getDetail());                    
                }
                
                logger.printf("%s - ==>%s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), result.getData().getResult());                
            };
            logger.println(LogParserUtil.SYMBOL_DETAIL_EPOS);
        }
        catch(Throwable t)
        {
            logger.printf("%s - [ERROR] %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), t.getMessage());
        }

        logger.println(LogParserUtil.SYMBOL_COLLECT_PROCEDURE_EPOS);
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> 
    {
        private static final String PLEASE_SELECT_TEXT = "--- SELECT ---";
        private static final ArrayList<ListBoxModel.Option> DEFAULT_DURATION_OPTIONS = new ArrayList<>();
        private static final ArrayList<ListBoxModel.Option> DEFAULT_FREQUENCY_OPTIONS = new ArrayList<>();
        static 
        {
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("1  minute", "60", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("5  minute", "300", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("10 minutes", "600", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("30 minutes", "1800", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("1  hour", "3600", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("4  hours", "14400", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("8  hours", "28800", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("16 hours", "57600", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("24 hours", "86400", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("48 hours", "172800", false));
            DEFAULT_DURATION_OPTIONS.add(new ListBoxModel.Option("72 hours", "259200", false));

            DEFAULT_FREQUENCY_OPTIONS.add(new ListBoxModel.Option("15 seconds", "15", false));
            DEFAULT_FREQUENCY_OPTIONS.add(new ListBoxModel.Option("30 seconds", "30", false));
            DEFAULT_FREQUENCY_OPTIONS.add(new ListBoxModel.Option("1  minute", "60", false));
            DEFAULT_FREQUENCY_OPTIONS.add(new ListBoxModel.Option("5  minutes", "300", false));
            DEFAULT_FREQUENCY_OPTIONS.add(new ListBoxModel.Option("10 minutes", "600", false));
        }

        private int lastEditorId = 0;

        @JavaScriptMethod
        public synchronized String createEditorId() 
        {
            return String.valueOf(lastEditorId++);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType)
        {
            return true;
        }

        public FormValidation doCheckDeviceId(
                @QueryParameter String value)
        {
            if(XStringUtil.isBlank(value))
            {
                return FormValidation.error("DEVICE ID IS BLANK");
            }
            
            if(!NameRule.isDevice(value))
            {
                return FormValidation.error("INVALID DEVICE ID: '" + value + "'");
            }

            final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
            if(XStringUtil.isBlank(endpoint))
            {
                return FormValidation.error("DEMETER ENDPOINT IS NOT CONFIGURED");
            }

            RESTfulClient client = new RESTfulClient.Builder()
                    .enableSSL(true)
                    .endpoint(endpoint).build();
            
            GetDeviceRequest getDeviceRequest = new GetDeviceRequest()
                    .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                    .withNodeName(value);

            GetDeviceResult getDeviceResult = client.getDevice(getDeviceRequest);
            if(200 != getDeviceResult.getStatusCode())
            {
                return FormValidation.error("SERVER ACK HTTP " + getDeviceResult.getStatusCode());
            }
            
            if(true == getDeviceResult.hasError())
            {
                UMEiError error = getDeviceResult.getErrors().get(0);
                return FormValidation.error("SERVER RESPONSE HAS ERROR, CODE: " + error.getCode() + ", DETAIL: " + error.getDetail());
            }

            if(0 != getDeviceResult.getData().getState().compareTo("online"))
            {
                return FormValidation.error("DEVICE '" + value + "' IS NOT ONLINE");
            }

            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteDeviceId(
                @QueryParameter String value) 
        {
            AutoCompletionCandidates completions = new AutoCompletionCandidates();

            final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
            do
            {
                if(XStringUtil.isBlank(endpoint))
                {
                    break;
                }
                
                RESTfulClient client = new RESTfulClient.Builder()
                        .enableSSL(true)
                        .endpoint(endpoint).build();
                
                GetDevicesRequest getDeviceRequest = new GetDevicesRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withFrom(0)
                        .withSize(500)
                        .withState("online");
                
                GetDevicesResult getDeviceResult = client.getDevices(getDeviceRequest);
                if(200 != getDeviceResult.getStatusCode() || true == getDeviceResult.hasError())
                {
                    break;
                }

                for(GetDevicesResult.ResultData row : getDeviceResult.getData())
                {
                    String deviceId = NameRule.formatDeviceName(row.getSerial(), row.getMac());
                    if(XStringUtil.isBlank(value) ||
                       deviceId.startsWith(value.toLowerCase()))
                    {
                        completions.add(deviceId);
                    }
                }
            }
            while(false);

            return completions;
        }

        public ListBoxModel doFillAppPublisherItems(
                @QueryParameter String deviceId) 
        {
            ListBoxModel listBoxModel = new ListBoxModel();            

            final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
            do
            {
                if(XStringUtil.isBlank(endpoint))
                {
                    break;
                }

                if(XStringUtil.isBlank(deviceId) ||
                   !NameRule.isDevice(deviceId))
                {
                    break;
                }

                RESTfulClient client = new RESTfulClient.Builder()
                        .enableSSL(true)
                        .endpoint(endpoint).build();

                GetDeviceRequest getDeviceRequest = new GetDeviceRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withNodeName(deviceId);
                
                GetDeviceResult getDeviceResult = client.getDevice(getDeviceRequest);
                if(200 != getDeviceResult.getStatusCode() || true == getDeviceResult.hasError())
                {
                    break;
                }
                
                final String model = getDeviceResult.getData().getModel();
                
                GetInstallableAppsRequest getInstallableAppsRequest = new GetInstallableAppsRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withModel(model)
                        .withFrom(0)
                        .withSize(500);
                
                GetInstallableAppsResult getInstallableAppsResult = client.getInstallableApps(getInstallableAppsRequest);
                if(200 != getInstallableAppsResult.getStatusCode() || true == getInstallableAppsResult.hasError())
                {
                    break;
                }
                
                if(false == getInstallableAppsResult.getData().isEmpty())
                {
                    // add a blank option to encourage users who must select an option
                    listBoxModel.add(PLEASE_SELECT_TEXT);
                }

                for(GetInstallableAppsResult.ResultData row : getInstallableAppsResult.getData())
                {
                    String publisher = row.getPublisher();

                    boolean added = false;                    
                    for(ListBoxModel.Option option : listBoxModel)
                    {
                        if(0 == option.name.compareTo(publisher))
                        {
                            added = true;
                            break;
                        }
                    }
                    
                    if(false == added)
                    {
                        listBoxModel.add(publisher);
                    }
                }
            }
            while(false);
            
            return listBoxModel;
        }

        public ListBoxModel doFillAppNameItems(
                @QueryParameter String deviceId,
                @QueryParameter String appPublisher) 
        {
            ListBoxModel listBoxModel = new ListBoxModel();

            final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
            do
            {
                if(XStringUtil.isBlank(endpoint))
                {
                    break;
                }

                if(XStringUtil.isBlank(deviceId) ||
                   !NameRule.isDevice(deviceId) || 
                   XStringUtil.isBlank(appPublisher) ||
                   0 == appPublisher.compareTo(PLEASE_SELECT_TEXT))
                {
                    break;
                }
                
                RESTfulClient client = new RESTfulClient.Builder()
                        .enableSSL(true)
                        .endpoint(endpoint).build();

                GetDeviceRequest getDeviceRequest = new GetDeviceRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withNodeName(deviceId);
                
                GetDeviceResult getDeviceResult = client.getDevice(getDeviceRequest);
                if(200 != getDeviceResult.getStatusCode() || true == getDeviceResult.hasError())
                {
                    break;
                }
                
                final String model = getDeviceResult.getData().getModel();

                GetInstallableAppsRequest getInstallableAppsRequest = new GetInstallableAppsRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withModel(model)
                        .withFrom(0)
                        .withSize(500);
                
                GetInstallableAppsResult getInstallableAppsResult = client.getInstallableApps(getInstallableAppsRequest);
                if(200 != getInstallableAppsResult.getStatusCode() || true == getInstallableAppsResult.hasError())
                {
                    break;
                }
                
                if(false == getInstallableAppsResult.getData().isEmpty())
                {
                    // add a blank option to encourage users who must select an option
                    listBoxModel.add(PLEASE_SELECT_TEXT);
                }
                
                for(GetInstallableAppsResult.ResultData row : getInstallableAppsResult.getData())
                {
                    String publisher = row.getPublisher();
                    if(0 != appPublisher.compareTo(publisher))
                    {
                        continue;
                    }

                    String appName = row.getAppName();

                    boolean added = false;                
                    for(ListBoxModel.Option option : listBoxModel)
                    {
                        if(0 == option.name.compareTo(appName))
                        {
                            added = true;
                            break;
                        }
                    }
                    
                    if(false == added)
                    {
                        listBoxModel.add(appName);
                    }
                }
            }
            while(false);
            
            return listBoxModel;
        }

        public ListBoxModel doFillAppVersionItems(
                @QueryParameter String deviceId,
                @QueryParameter String appPublisher,
                @QueryParameter String appName) 
        {
            ListBoxModel listBoxModel = new ListBoxModel();

            final String endpoint = OpenWRTPlugin.getDemeterPluginDescriptor().getEndpoint();
            do
            {
                if(XStringUtil.isBlank(endpoint))
                {
                    break;
                }

                if(XStringUtil.isBlank(deviceId) ||
                   !NameRule.isDevice(deviceId) || 
                   XStringUtil.isBlank(appPublisher) ||
                   0 == appPublisher.compareTo(PLEASE_SELECT_TEXT) ||
                   XStringUtil.isBlank(appName) ||
                   0 == appName.compareTo(PLEASE_SELECT_TEXT))
                {
                    break;
                }

                RESTfulClient client = new RESTfulClient.Builder()
                        .enableSSL(true)
                        .endpoint(endpoint).build();

                GetDeviceRequest getDeviceRequest = new GetDeviceRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withNodeName(deviceId);
                
                GetDeviceResult getDeviceResult = client.getDevice(getDeviceRequest);
                if(200 != getDeviceResult.getStatusCode() || true == getDeviceResult.hasError())
                {
                    break;
                }
                
                final String model = getDeviceResult.getData().getModel();

                GetInstallableAppsRequest getInstallableAppsRequest = new GetInstallableAppsRequest()
                        .withOriginatorId(OpenWRTPlugin.ORIGINATOR_ID)
                        .withModel(model)
                        .withFrom(0)
                        .withSize(500);
                
                GetInstallableAppsResult getInstallableAppsResult = client.getInstallableApps(getInstallableAppsRequest);
                if(200 != getInstallableAppsResult.getStatusCode() || true == getInstallableAppsResult.hasError())
                {
                    break;
                }
                
                if(false == getInstallableAppsResult.getData().isEmpty())
                {
                    // add a blank option to encourage users who must select an option
                    listBoxModel.add(PLEASE_SELECT_TEXT);
                }
                
                for(GetInstallableAppsResult.ResultData row : getInstallableAppsResult.getData())
                {
                    String publisher = row.getPublisher();
                    if(0 != appPublisher.compareTo(publisher))
                    {
                        continue;
                    }

                    String name = row.getAppName();
                    if(0 != appName.compareTo(name))
                    {
                        continue;
                    }

                    for(GetInstallableAppsResult.ResultData.Version aVersion : row.getVersions())
                    {
                        listBoxModel.add(aVersion.getVersionName());
                    }
                }
            }
            while(false);
            
            return listBoxModel;
        }

        public ListBoxModel doFillDurationItems(
                @QueryParameter String duration) 
        {
            ListBoxModel listBoxModel = new ListBoxModel();

            for(ListBoxModel.Option option : DEFAULT_DURATION_OPTIONS)
            {
                if(XStringUtil.isBlank(duration))
                {
                    if(0 == option.value.compareTo("600"))
                    {
                        // default selection
                        listBoxModel.add(new ListBoxModel.Option(option.name, option.value, true));
                    }
                    else
                    {
                        listBoxModel.add(option);
                    }
                }
                else
                {
                    if(0 == option.value.compareTo(duration))
                    {
                        // selected
                        listBoxModel.add(new ListBoxModel.Option(option.name, option.value, true));
                    }
                    else
                    {
                        listBoxModel.add(option);
                    }
                }
            }

            return listBoxModel;
        }

        public ListBoxModel doFillFrequencyItems(
                @QueryParameter String frequency) 
        {
            ListBoxModel listBoxModel = new ListBoxModel();

            for(ListBoxModel.Option option : DEFAULT_FREQUENCY_OPTIONS)
            {
                if(XStringUtil.isBlank(frequency))
                {
                    if(0 == option.value.compareTo("30"))
                    {
                        // default selection
                        listBoxModel.add(new ListBoxModel.Option(option.name, option.value, true));
                    }
                    else
                    {
                        listBoxModel.add(option);
                    }
                }
                else
                {
                    if(0 == option.value.compareTo(frequency))
                    {
                        // selected
                        listBoxModel.add(new ListBoxModel.Option(option.name, option.value, true));
                    }
                    else
                    {
                        listBoxModel.add(option);
                    }
                }
            }

            return listBoxModel;
        }

        @Override
        public String getDisplayName() 
        {
            return Messages.DemeterCollectBuilder_DescriptorImpl_DisplayName();
        }        
    }
}
