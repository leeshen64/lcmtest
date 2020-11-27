package io.jenkins.plugins.sercomm.openwrt;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.kohsuke.stapler.DataBoundConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.sercomm.commons.util.DateTime;
import com.sercomm.commons.util.Json;
import com.sercomm.commons.util.XStringUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

@SuppressFBWarnings(value = {"ICAST_IDIV_CAST_TO_DOUBLE","NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
public class OpenWRTReportPublisher extends Recorder
{
    private String filePrefix;
    
    @DataBoundConstructor
    public OpenWRTReportPublisher(
            String filePrefix)
    {
        this.filePrefix = filePrefix;
    }

    public String getFilePrefix()
    {
        return this.filePrefix;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() 
    {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
    throws InterruptedException 
    {
        final PrintStream logger = listener.getLogger();
        
        FilePath cpuChartFile = null;
        FilePath ramChartFile = null;
        FilePath storageChartFile = null;
        FilePath htmlFile = null;
        
        logger.println();
        logger.printf("%s - [INFO] ====== Generating Report ======%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS));
        try
        {
            boolean isLoopProcSection = false;
            boolean isLoopProcSummarySection = false;
            
            boolean isCollectProcSection = false;
            boolean isCollectProcDetailSection = false;
            
            StringBuilder markdownText = new StringBuilder();
            StringBuilder rawText = new StringBuilder();
            TimeSeries cpuTimeSeries = new TimeSeries("CPU Usage");
            TimeSeries ramTimeSeries = new TimeSeries("RAM Usage");
            TimeSeries storageTimeSeries = new TimeSeries("Storage Usage");
            
            DateTime beginTime = null;
            DateTime endTime = null;
            
            try(BufferedReader reader = new BufferedReader(build.getLogReader()))
            {
                String line = null;
                int totalLoopCount = 0;
                
                while((line = reader.readLine()) != null)
                {
                    if(XStringUtil.isBlank(line) ||
                       line.contains("Started by user"))
                    {
                        continue;
                    }
                    
                    if(false == line.startsWith("@"))
                    {
                        rawText.append(line).append("\n");
                    }

                    DateTime dateTime = LogParserUtil.parseDateTime(line);
                    if(null == beginTime && null != dateTime)
                    {
                        beginTime = dateTime;
                    }
                    
                    if(null != dateTime)
                    {
                        endTime = dateTime;
                    }
                    
                    if(line.contains(LogParserUtil.SYMBOL_LOOP_PROCEDURE_BPOS))
                    {
                        isLoopProcSection = true;
                        logger.printf("%s - [INFO] parsing loop installation results... %n", DateTime.now().toString(DateTime.FORMAT_ISO_MS));

                        markdownText.append("# Loop Installation Test Result").append("\n");
                        markdownText.append("---").append("\n");

                        markdownText.append("| Step Name | Total Loop   | Done     | Successful Rate |").append("\n");
                        markdownText.append("| --------  | --------     | -------- | --------        |").append("\n");
                        continue;
                    }
                    
                    if(line.contains(LogParserUtil.SYMBOL_LOOP_PROCEDURE_EPOS))
                    {
                        isLoopProcSection = false;
                        continue;
                    }
                    
                    if(line.contains(LogParserUtil.SYMBOL_COLLECT_PROCEDURE_BPOS))
                    {
                        isCollectProcSection = true;
                        logger.printf("%s - [INFO] parsing resource consumption results... %n", DateTime.now().toString(DateTime.FORMAT_ISO_MS));

                        markdownText.append("# Resource Consumption Test Result").append("\n");
                        continue;
                    }
                    
                    if(line.contains(LogParserUtil.SYMBOL_COLLECT_PROCEDURE_EPOS))
                    {
                        isCollectProcSection = false;
                        continue;
                    }
                    
                    if(isLoopProcSection && line.contains(LogParserUtil.SYMBOL_SUMMARY_BPOS))
                    {
                        isLoopProcSummarySection = true;
                        continue;
                    }
                    
                    if(isLoopProcSummarySection && line.contains(LogParserUtil.SYMBOL_SUMMARY_EPOS))
                    {
                        isLoopProcSummarySection = false;
                        continue;
                    }
                    
                    if(isCollectProcSection && line.contains(LogParserUtil.SYMBOL_DETAIL_BPOS))
                    {
                        isCollectProcDetailSection = true;
                        continue;
                    }

                    if(isCollectProcSection && line.contains(LogParserUtil.SYMBOL_DETAIL_EPOS))
                    {
                        isCollectProcDetailSection = false;
                        continue;
                    }
                    
                    // parse loop installation summary
                    if(isLoopProcSummarySection)
                    {
                        if(line.contains(LogParserUtil.SYMBOL_TOTAL_LOOP_COUNT))
                        {
                            String[] tokens = line.split("==>");
                            totalLoopCount = Integer.parseInt(tokens[1]);
                        }

                        if(line.contains(LogParserUtil.SYMBOL_INSTALL_APP_OK_COUNT))
                        {
                            String[] tokens = line.split("==>");
                            int value = Integer.parseInt(tokens[1]);
                            markdownText.append("|")
                                        .append("Installation")
                                        .append("|")
                                        .append(totalLoopCount)
                                        .append("|")
                                        .append(value)
                                        .append("|")
                                        .append(String.format("%.2f%%", (value / totalLoopCount) * 100.))
                                        .append("|")
                                        .append("\n");
                        }
                        
                        if(line.contains(LogParserUtil.SYMBOL_UNINSTALL_APP_OK_COUNT))
                        {
                            String[] tokens = line.split("==>");
                            int value = Integer.parseInt(tokens[1]);
                            markdownText.append("|")
                                .append("Uninstallation")
                                .append("|")
                                .append(totalLoopCount)
                                .append("|")
                                .append(value)
                                .append("|")
                                .append(String.format("%.2f%%", (value / totalLoopCount) * 100.))
                                .append("|")
                                .append("\n");
                        }

                        if(line.contains(LogParserUtil.SYMBOL_START_APP_OK_COUNT))
                        {
                            String[] tokens = line.split("==>");
                            int value = Integer.parseInt(tokens[1]);
                            markdownText.append("|")
                                .append("Start App")
                                .append("|")
                                .append(totalLoopCount)
                                .append("|")
                                .append(value != 0 ? value : "N/A")
                                .append("|")
                                .append(value != 0 ? String.format("%.2f%%", (value / totalLoopCount) * 100.) : "N/A")
                                .append("|")
                                .append("\n");
                        }

                        if(line.contains(LogParserUtil.SYMBOL_STOP_APP_OK_COUNT))
                        {
                            String[] tokens = line.split("==>");
                            int value = Integer.parseInt(tokens[1]);
                            markdownText.append("|")
                                .append("Stop App")
                                .append("|")
                                .append(totalLoopCount)
                                .append("|")
                                .append(value != 0 ? value : "N/A")
                                .append("|")
                                .append(value != 0 ? String.format("%.2f%%", (value / totalLoopCount) * 100.) : "N/A")
                                .append("|")
                                .append("\n");
                        }
                    }
                    
                    // parse collected resource consumption logs
                    if(isCollectProcDetailSection)
                    {                        
                        String[] tokens = line.split("==>");
                        if(2 != tokens.length)
                        {
                            continue;
                        }

                        try
                        {
                            JsonNode rootNode = Json.parse(tokens[1]);                        
                            ArrayList<ContainerInfo> containers = Json.mapper().readValue(
                                Json.mapper().treeAsTokens(rootNode.findPath("List")), 
                                Json.JavaTypeUtil.collectionType(
                                    ArrayList.class, 
                                    ContainerInfo.class));
                            
                            if(0 == containers.size())
                            {
                                continue;
                            }
                            
                            ContainerInfo container = containers.get(0);
                            
                            double value = 0.;
                            if(null != container.resources.cpu)
                            {
                                value = Double.parseDouble(container.resources.cpu.usage.replaceAll("%", XStringUtil.BLANK));
                                cpuTimeSeries.add(
                                    new Second(
                                        dateTime.getSecond(),
                                        dateTime.getMinute(),
                                        dateTime.getHour(),
                                        dateTime.getDay(),
                                        dateTime.getMonth(),
                                        dateTime.getYear()), 
                                    value);
                            }
                            
                            if(null != container.resources.memory)
                            {
                                value = container.resources.memory.usage * 100.;
                                value = Double.parseDouble(String.format("%.2f", value));
                                ramTimeSeries.add(
                                    new Second(
                                        dateTime.getSecond(),
                                        dateTime.getMinute(),
                                        dateTime.getHour(),
                                        dateTime.getDay(),
                                        dateTime.getMonth(),
                                        dateTime.getYear()), 
                                    value);
                            }

                            if(null != container.resources.storage)
                            {
                                value = container.resources.storage.usage * 100.;
                                value = Double.parseDouble(String.format("%.2f", value));
                                storageTimeSeries.add(
                                    new Second(
                                        dateTime.getSecond(),
                                        dateTime.getMinute(),
                                        dateTime.getHour(),
                                        dateTime.getDay(),
                                        dateTime.getMonth(),
                                        dateTime.getYear()), 
                                    value);                        
                            }
                        }
                        catch(Throwable t)
                        {
                            logger.printf("%s - [ERROR] %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), t.getMessage());
                            continue;
                        }
                    }
                };

                markdownText.append("### System Wide Charts").append("\n");
                markdownText.append("---").append("\n");

                JFreeChart chart;
                chart = createChart(
                    new TimeSeriesCollection(cpuTimeSeries),
                    "CPU Consumption",
                    "Time (sec.)",
                    "Usage (pct.)");
                
                cpuChartFile = build.getWorkspace().createTempFile(
                    XStringUtil.isBlank(this.filePrefix) ? 
                            String.format("%s-cpu-", build.getId()) :
                            String.format("%s-%s-cpu-", this.filePrefix, build.getId()),
                    ".jpg");
                ChartUtils.writeChartAsJPEG(cpuChartFile.write(), chart, 600, 200);
                markdownText.append("![](").append(cpuChartFile.getName()).append(")\n");
                markdownText.append("---").append("\n");
                
                chart = createChart(
                    new TimeSeriesCollection(ramTimeSeries),
                    "RAM Consumption",
                    "Time (sec.)",
                    "Usage (pct.)");

                ramChartFile = build.getWorkspace().createTempFile(
                    XStringUtil.isBlank(this.filePrefix) ? 
                            String.format("%s-ram-", build.getId()) :
                            String.format("%s-%s-ram-", this.filePrefix, build.getId()),
                    ".jpg");
                ChartUtils.writeChartAsJPEG(ramChartFile.write(), chart, 600, 200);
                markdownText.append("![](").append(ramChartFile.getName()).append(")\n");                
                markdownText.append("---").append("\n");
                
                chart = createChart(
                    new TimeSeriesCollection(storageTimeSeries),
                    "Storage Consumption",
                    "Time (sec.)",
                    "Usage (pct.)");

                storageChartFile = build.getWorkspace().createTempFile(
                    XStringUtil.isBlank(this.filePrefix) ? 
                            String.format("%s-storage-", build.getId()) :
                            String.format("%s-%s-storage-", this.filePrefix, build.getId()),
                    ".jpg");
                ChartUtils.writeChartAsJPEG(storageChartFile.write(), chart, 600, 200);
                markdownText.append("![](").append(storageChartFile.getName()).append(")\n");
                markdownText.append("---").append("\n");

                // TODO:
                markdownText.append("## Container Wide Charts").append("\n");
                markdownText.append("---").append("\n");
                
                markdownText.append("\n# Raw Text Log").append("\n");
                markdownText.append("---").append("\n");
                markdownText.append("```").append("\n");
                markdownText.append(rawText);
                markdownText.append("```").append("\n");
                
                
                markdownText.insert(0, "| End Time   | " + endTime.toString(DateTime.FORMAT_ISO_MS) + " |\n");
                markdownText.insert(0, "| Begin Time | " + beginTime.toString(DateTime.FORMAT_ISO_MS) + " |\n");
                markdownText.insert(0, "| -----      | -----     |\n");
                markdownText.insert(0, "|            | Timestamp |\n");
                
                markdownText.insert(0, "---\n");
                markdownText.insert(0, "\n# Information\n");
                
                String markdownString = markdownText.toString();
                
                List<org.commonmark.Extension> extensions = 
                        Arrays.asList(TablesExtension.create());
                
                Parser parser = Parser.builder()
                        .extensions(extensions)
                        .build();
                
                Node document = parser.parse(markdownString);
                
                HtmlRenderer renderer = HtmlRenderer.builder()
                        .extensions(extensions)
                        .build();
                
                String htmlText = renderer.render(document);
                
                htmlText = "<html>\r\n<head>\r\n</head>\r\n<body>\r\n" + htmlText + "</body>\r\n</html>\r\n";
                
                htmlFile = build.getWorkspace().createTextTempFile(
                    XStringUtil.isBlank(this.filePrefix) ? 
                            String.format("%s-output-", build.getId()) :
                            String.format("%s-%s-output-", this.filePrefix, build.getId()),
                    ".html", 
                    htmlText);

                FilePath pdfFile = build.getWorkspace().createTempFile(
                    XStringUtil.isBlank(this.filePrefix) ? 
                            String.format("%s-output-", build.getId()) :
                            String.format("%s-%s-output-", this.filePrefix, build.getId()),
                    ".pdf");
                
                try(OutputStream outputStream = pdfFile.write())
                {
                    PdfRendererBuilder builder = new PdfRendererBuilder();
                    builder.useFastMode();
                    builder.withFile(new File(htmlFile.getRemote()));
                    builder.toStream(outputStream);
                    builder.run();
                }
            }
        }
        catch(Throwable t)
        {
            logger.println();
            logger.printf("%s - [ERROR] %s%n", DateTime.now().toString(DateTime.FORMAT_ISO_MS), t.getMessage());
        }

        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> 
    {
        @Override
        public String getDisplayName() 
        {
            return Messages.DemeterReportPublisher_DescriptorImpl_DisplayName();
        }
        
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType)
        {
            return true;
        }
    }

    private static JFreeChart createChart(
            XYDataset dataset,
            String title,
            String timeAxisLabel,
            String valueAxisLabel) 
    {    
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            title,          // title
            timeAxisLabel,  // X-Axis Label
            valueAxisLabel, // Y-Axis Label
            dataset);

        XYPlot plot = (XYPlot)chart.getPlot();
        plot.setBackgroundPaint(new Color(255,255,255));

        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);        

        return chart;
    }

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD")
    protected final static class ContainerInfo
    {
        public final static class CPU
        {
            @JsonProperty("Usage")
            private String usage = "0.0";
        }
        
        public final static class Memory
        {
            @JsonProperty("Total")
            private Long total = 0L;
            @JsonProperty("Free")
            private Long free = 0L;
            @JsonProperty("Usage")
            private Double usage = 0.;
        }

        public final static class Storage
        {
            @JsonProperty("Total")
            private Long total = 0L;
            @JsonProperty("Free")
            private Long free = 0L;
            @JsonProperty("Usage")
            private Double usage = 0.;
        }
        
        public final static class Resources
        {
            @JsonProperty("CPU")
            private CPU cpu;
            @JsonProperty("Storage")
            private Storage storage;
            @JsonProperty("Memory")
            private Memory memory;
        }
        
        @JsonProperty("Id")
        private String id;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Enabled")
        private Boolean enabled;
        @JsonProperty("Version")
        private String version;
        @JsonProperty("Vendor")
        private String vendor;
        @JsonProperty("Type")
        private String type;
        @JsonProperty("Status")
        private String status;
        @JsonProperty("Resources")
        private Resources resources;
    }
}
