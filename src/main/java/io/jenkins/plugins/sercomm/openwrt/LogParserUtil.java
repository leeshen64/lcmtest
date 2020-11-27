package io.jenkins.plugins.sercomm.openwrt;

import com.sercomm.commons.util.DateTime;

public class LogParserUtil
{
    public static final String SYMBOL_LOOP_PROCEDURE_BPOS = "@>loop";
    public static final String SYMBOL_LOOP_PROCEDURE_EPOS = "@<loop";
    public static final String SYMBOL_COLLECT_PROCEDURE_BPOS = "@>collect";
    public static final String SYMBOL_COLLECT_PROCEDURE_EPOS = "@<collect";

    public static final String SYMBOL_DESCRIBE_BPOS = "@>describe";
    public static final String SYMBOL_DESCRIBE_EPOS = "@<describe";
    public static final String SYMBOL_DETAIL_BPOS = "@>detail";
    public static final String SYMBOL_DETAIL_EPOS = "@<detail";
    public static final String SYMBOL_SUMMARY_BPOS = "@>summary";
    public static final String SYMBOL_SUMMARY_EPOS = "@<summary";

    public static final String SYMBOL_TOTAL_LOOP_COUNT = "* total procedure count ==>";
    public static final String SYMBOL_INSTALL_APP_OK_COUNT = "* install app successfully ==>";
    public static final String SYMBOL_UNINSTALL_APP_OK_COUNT = "* uninstall app successfully ==>";
    public static final String SYMBOL_START_APP_OK_COUNT = "* start app successfully ==>";
    public static final String SYMBOL_STOP_APP_OK_COUNT = "* stop app successfully ==>";

    public static DateTime parseDateTime(final String line)
    {        
        DateTime dateTime;
        try
        {
            String value = line.substring(0, 28);
            dateTime = DateTime.from(value, DateTime.FORMAT_ISO_MS);
        }
        catch(Exception e)
        {
            return null;
        }
        
        return dateTime;
    }
}
