package com.kinnara.kecakplugins.jasperreports;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.userview.model.Element;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.joget.plugin.base.PluginManager;

import javax.servlet.ServletOutputStream;
import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class JasperFileUpload extends FileUpload {
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        File f = new File("");
        try(FileOutputStream output = new FileOutputStream(f)) {
            JasperPrint print = getReport();
            JasperExportManager.exportReportToPdfStream(print, output);
            FileUtil.storeFile(f, this, formData.getPrimaryKeyValue());
        } catch (Exception ex) {
            LogUtil.error(this.getClass().getName(), (Throwable)ex, "");
            HashMap<String, Object> model = new HashMap<>();
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }

        return super.renderTemplate(formData, dataModel);
    }

    protected JasperPrint getReport() throws Exception {
        Map dsMap;
        Object dsProperties;
        String jrxml = getPropertyString("jrxml"); // TODO
        if (!JasperCompileManager.class.getClassLoader().equals(UserviewMenu.class.getClassLoader())) {
            jrxml = jrxml.replaceAll("language=\"groovy\"", "");
        }
        ByteArrayInputStream input = new ByteArrayInputStream(jrxml.getBytes("UTF-8"));
        JasperReport report = JasperCompileManager.compileReport((InputStream)input);
        DataSource ds = null;
        Object datasource = getProperty("datasource"); // TODO
        if (datasource != null && datasource instanceof Map && (dsMap = (Map)datasource) != null && dsMap.containsKey("classname") && !dsMap.get("className").toString().isEmpty() && (dsProperties = dsMap.get("properties")) != null && dsProperties instanceof Map) {
            Map<String, String> dsProps = (Map)dsProperties;
            String jdbcDriver = dsProps.get("jdbcDriver");
            String jdbcUrl = dsProps.get("jdbcUrl");
            String jdbcUser = dsProps.get("jdbcUser");
            String jdbcPassword = dsProps.get("jdbcPassword");
            Properties props = new Properties();
            props.put("driverClassName", jdbcDriver);
            props.put("url", jdbcUrl);
            props.put("username", jdbcUser);
            props.put("password", jdbcPassword);
            LogUtil.debug(this.getClass().getName(), ("Using custom datasource " + jdbcUrl));
            ds = BasicDataSourceFactory.createDataSource((Properties)props);
        }
        if (ds == null) {
            LogUtil.debug(this.getClass().getName(), "Using current profile datasource");
            ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
        }
        if (ds != null) {
            HashMap hm = new HashMap();
            Object[] parameters = (Object[])getProperty("parameters"); // TODO
            if (parameters != null && parameters.length > 0) {
                for (Object o : parameters) {
                    HashMap parameter = (HashMap)o;
                    hm.put(parameter.get("name"), parameter.get("value"));
                }
            }
            JRSwapFileVirtualizer virtualizer = null;
            if ("true".equals(getProperty("use_virtualizer"))) { // TODO
                String path = SetupManager.getBaseDirectory() + "temp_jasper_swap";
                File filepath = new File(path);
                if (!filepath.exists()) {
                    filepath.mkdirs();
                }
                virtualizer = new JRSwapFileVirtualizer(300, new JRSwapFile(filepath.getAbsolutePath(), 4096, 100), true);
                hm.put("REPORT_VIRTUALIZER", (JRSwapFileVirtualizer)virtualizer);
            }
            Connection conn = null;
            JasperPrint print = null;
            try {
                conn = ds.getConnection();
                print = JasperFillManager.fillReport(report, hm, conn);
            }
            finally {
                if (conn != null) {
                    conn.close();
                }
            }
            return print;
        }
        return null;
    }

    @Override
    public String getName() {
        return "Jasper File";
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }
}
