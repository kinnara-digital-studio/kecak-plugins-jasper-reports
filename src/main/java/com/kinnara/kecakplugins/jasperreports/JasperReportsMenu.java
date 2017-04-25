package com.kinnara.kecakplugins.jasperreports;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewCategory;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginProperty;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.beans.BeansException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRRenderable;
import net.sf.jasperreports.engine.JRWrappingSvgRenderer;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRHtmlExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;

public class JasperReportsMenu extends UserviewMenu implements PluginWebSupport {
    public String getName() {
        return "Kecak Jasper Reports";
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
        return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    public PluginProperty[] getPluginProperties() {
        return null;
    }

    public Object execute(Map properties) {
        return null;
    }

    public String getCategory() {
        return "Kecak Enterprise";
    }

    public String getIcon() {
        return "/plugin/" + getClassName() + "/images/grid_icon.gif";
    }

    public String getRenderPage() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String menuId = this.getPropertyString("customId");
        if (menuId == null || menuId.trim().isEmpty()) {
            menuId = this.getPropertyString("id");
        }
        String reportUrl = "/web/json/plugin/" + getClassName() + "/service?action=report&appId=" + appDef.getId() + "&appVersion=" + appDef.getVersion() + "&userviewId=" + this.getUserview().getPropertyString("id") + "&menuId=" + menuId;
        if (!"true".equals(this.getRequestParameter("isPreview"))) {
            for (String key : ((Map<String, String>)this.getRequestParameters()).keySet()) {
                if (key.matches("appId|appVersion|userviewId|menuId|isPreview|embed|contextPath")) continue;
                reportUrl = StringUtil.addParamsToUrl(reportUrl, key, this.getRequestParameterString(key));
            }
        }
        this.setProperty("includeUrl", reportUrl);
        String contextPath = AppUtil.getRequestContextPath();
        String cssUrl = contextPath + "/plugin/" + getClassName() + "/css/jasper.css";
        String header = "<link rel=\"stylesheet\" href=\"" + cssUrl + "\" />";
        String customHeader = this.getPropertyString("customHeader");
        if (customHeader != null) {
            header = header + customHeader;
        }
        this.setProperty("includeHeader", header);
        String pdfUrl = contextPath + reportUrl + "&type=pdf";
        String excelUrl = contextPath + reportUrl + "&type=xls";
        String footer = "<div class=\"exportlinks\">";
        String exportProperty = this.getPropertyString("export");
        if (exportProperty != null && exportProperty.contains("pdf")) {
            footer = footer + "<a href=\"" + pdfUrl + "\" target=\"_blank\"><span class=\"export pdf\">PDF</span></a>";
        }
        if (exportProperty != null && exportProperty.contains("xls")) {
            footer = footer + "<a href=\"" + excelUrl + "\" target=\"_blank\"><span class=\"export excel\">Excel</span></a>";
        }
        footer = footer + "</div>";
        String customFooter = this.getPropertyString("customFooter");
        if (customFooter != null) {
            footer = footer + customFooter;
        }
        this.setProperty("includeFooter", footer);
        String body = this.generateReport();
        String result = header + body + footer;
        return result;
    }

    public String getJspPage() {
        return null;
    }

    public boolean isHomePageSupported() {
        return true;
    }

    public String getDecoratedMenu() {
        return null;
    }

    public String getLabel() {
        return getName();
    }

    public String getClassName() {
        return this.getClass().getName();
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion, appId, appVersion};
        String json = AppUtil.readPluginResource(this.getClass().getName(), "/properties/jasperReports.json", (Object[])arguments, (boolean)true, "message/jasperReports");
        return json;
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        block9 : {
            ServletOutputStream output = response.getOutputStream();
            try {
                String action = request.getParameter("action");
                String imageName = request.getParameter("image");
                if ("report".equals(action)) {
                    UserviewMenu selectedMenu = null;
                    String appId = request.getParameter("appId");
                    String appVersion = request.getParameter("appVersion");
                    String userviewId = request.getParameter("userviewId");
                    String key = request.getParameter("key");
                    String menuId = request.getParameter("menuId");
                    String type = request.getParameter("type");
                    String contextPath = request.getContextPath();
                    Map parameterMap = request.getParameterMap();
                    String json = request.getParameter("json");
                    AppDefinition appDef = null;
                    if (appId != null && appVersion != null) {
                        AppService appService = (AppService)AppUtil.getApplicationContext().getBean("appService");
                        appDef = appService.getAppDefinition(appId, appVersion.toString());
                    }
                    selectedMenu = json != null && !json.trim().isEmpty() ? this.findUserviewMenuFromPreview(json, menuId, contextPath, parameterMap, key) : this.findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath, parameterMap);
                    if (selectedMenu != null) {
                        this.generateReport(selectedMenu, type, (OutputStream)output, request, response);
                    }
                    break block9;
                }
                if (imageName != null && !imageName.trim().isEmpty()) {
                    this.generateImage(request, response);
                    break block9;
                }
                response.setStatus(204);
            }
            catch (Exception ex) {
                LogUtil.error(this.getClass().getName(), (Throwable)ex, "");
                HashMap<String, Object> model = new HashMap<String, Object>();
                model.put("request", request);
                model.put("exception", ex);
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                String content = pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
                response.setContentType("text/html");
                output.write(content.getBytes("UTF-8"));
            }
            finally {
                output.close();
            }
        }
    }

    protected UserviewMenu findUserviewMenuFromPreview(String json, String menuId, String contextPath, Map parameterMap, String key) throws BeansException {
        UserviewService userviewService = (UserviewService)AppUtil.getApplicationContext().getBean("userviewService");
        Userview userview = userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key, Boolean.valueOf(true));
        UserviewMenu selectedMenu = this.findUserviewMenuInUserview(userview, menuId);
        return selectedMenu;
    }

    protected UserviewMenu findUserviewMenuFromDef(AppDefinition appDef, String userviewId, String menuId, String key, String contextPath, Map parameterMap) throws BeansException {
        UserviewMenu selectedMenu = null;
        UserviewService userviewService = (UserviewService)AppUtil.getApplicationContext().getBean("userviewService");
        UserviewDefinitionDao userviewDefinitionDao = (UserviewDefinitionDao)AppUtil.getApplicationContext().getBean("userviewDefinitionDao");
        UserviewDefinition userviewDef = (UserviewDefinition)userviewDefinitionDao.loadById(userviewId, appDef);
        if (userviewDef != null) {
            String json = userviewDef.getJson();
            Userview userview = userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key, Boolean.valueOf(true));
            selectedMenu = this.findUserviewMenuInUserview(userview, menuId);
        }
        return selectedMenu;
    }

    protected UserviewMenu findUserviewMenuInUserview(Userview userview, String menuId) {
        UserviewMenu selectedMenu = null;
        boolean found = false;
        Collection<UserviewCategory> categories = userview.getCategories();
        for (UserviewCategory category : categories) {
            Collection<UserviewMenu> menus = category.getMenus();
            for (UserviewMenu menu : menus) {
                if (!menuId.equals(menu.getPropertyString("customId")) && !menuId.equals(menu.getPropertyString("id"))) continue;
                selectedMenu = menu;
                found = true;
                break;
            }
            if (!found) continue;
            break;
        }
        return selectedMenu;
    }

    protected JasperPrint getReport(UserviewMenu menu) throws JRException, SQLException, UnsupportedEncodingException, Exception {
        Map dsMap;
        Object dsProperties;
        String jrxml = menu.getPropertyString("jrxml");
        if (!JasperCompileManager.class.getClassLoader().equals(UserviewMenu.class.getClassLoader())) {
            jrxml = jrxml.replaceAll("language=\"groovy\"", "");
        }
        ByteArrayInputStream input = new ByteArrayInputStream(jrxml.getBytes("UTF-8"));
        JasperReport report = JasperCompileManager.compileReport((InputStream)input);
        DataSource ds = null;
        Object datasource = menu.getProperty("datasource");
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
            Object[] parameters = (Object[])menu.getProperty("parameters");
            if (parameters != null && parameters.length > 0) {
                for (Object o : parameters) {
                    HashMap parameter = (HashMap)o;
                    hm.put(parameter.get("name"), parameter.get("value"));
                }
            }
            JRSwapFileVirtualizer virtualizer = null;
            if ("true".equals(menu.getProperty("use_virtualizer"))) {
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
                print = JasperFillManager.fillReport((JasperReport)report, hm, (Connection)conn);
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

    protected String generateReport() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            JasperPrint print = this.getReport(this);
            if (print != null) {
                String menuId = this.getPropertyString("customId");
                if (menuId == null || menuId.trim().isEmpty()) {
                    menuId = this.getPropertyString("id");
                }
                LogUtil.debug(this.getClass().getName(), ("Generating HTML report for " + menuId));
                JRHtmlExporter jrHtmlExporter = new JRHtmlExporter();
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.JASPER_PRINT, print);
                HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
                if (request != null) {
                    request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
                }
                String imagesUri = AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?image=";
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.IMAGES_URI, imagesUri);
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.OUTPUT_STREAM, output);
                jrHtmlExporter.setParameter(JRExporterParameter.CHARACTER_ENCODING, "UTF-8");
                jrHtmlExporter.exportReport();
                return new String(output.toByteArray(), "UTF-8");
            }
        }
        catch (Exception e) {
            LogUtil.error(this.getClass().getName(), (Throwable)e, "");
            HashMap<String, Exception> model = new HashMap<String, Exception>();
            model.put("exception", e);
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }
        return "";
    }

    protected void generateReport(UserviewMenu menu, String type, OutputStream output, HttpServletRequest request, HttpServletResponse response) throws Exception, IOException, JRException, BeansException, UnsupportedEncodingException, SQLException {
        JasperPrint print;
        String menuId = menu.getPropertyString("customId");
        if (menuId == null || menuId.trim().isEmpty()) {
            menuId = menu.getPropertyString("id");
        }
        if ((print = this.getReport(menu)) != null) {
            if ("pdf".equals(type)) {
                if (response != null) {
                    response.setHeader("Content-Type", "application/pdf");
                    response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".pdf");
                }
                LogUtil.debug(this.getClass().getName(), ("Generating PDF report for " + menuId));
                JasperExportManager.exportReportToPdfStream((JasperPrint)print, (OutputStream)output);
            } else if ("xls".equals(type)) {
                if (response != null) {
                    response.setHeader("Content-Type", "application/vnd.ms-excel");
                    response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".xls");
                }
                LogUtil.debug(this.getClass().getName(), ("Generating XLS report for " + menuId));
                JRXlsExporter exporter = new JRXlsExporter();
                exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, output);
                exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
                exporter.setParameter(JRExporterParameter.CHARACTER_ENCODING, "UTF-8");
                exporter.exportReport();
            } else {
                if (response != null) {
                    response.setHeader("Content-Type", "text/html; charset=UTF-8");
                    response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".html");
                }
                LogUtil.debug(this.getClass().getName(), ("Generating HTML report for " + menuId));
                JRHtmlExporter jrHtmlExporter = new JRHtmlExporter();
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.JASPER_PRINT, print);
                if (request != null) {
                    request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
                }
                String imagesUri = AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?image=";
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.IMAGES_URI, imagesUri);
                jrHtmlExporter.setParameter(JRHtmlExporterParameter.OUTPUT_STREAM, output);
                jrHtmlExporter.setParameter(JRExporterParameter.CHARACTER_ENCODING, "UTF-8");
                jrHtmlExporter.exportReport();
            }
        }
    }

    protected void generateImage(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        byte[] imageData = null;
        String imageMimeType = null;
        String imageName = request.getParameter("image");
        if ("px".equals(imageName)) {
            InputStream input = null;
            try {
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                input = pluginManager.getPluginResource(this.getClass().getName(), "net/sf/jasperreports/engine/images/pixel.GIF");
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                byte[] bbuf = new byte[65536];
                int length = 0;
                while ((length = input.read(bbuf)) != -1) {
                    stream.write(bbuf, 0, length);
                }
                imageData = stream.toByteArray();
                imageMimeType = "image/gif";
            }
            catch (Exception e) {
                throw new ServletException((Throwable)e);
            }
            finally {
                if (input != null) {
                    input.close();
                }
            }
        }
        List<JasperPrint> jasperPrintList = BaseHttpServlet.getJasperPrintList((HttpServletRequest)request);
        if (jasperPrintList == null) {
            throw new ServletException("No JasperPrint documents found on the HTTP session.");
        }
        JRPrintImage image = JRHtmlExporter.getImage(jasperPrintList, imageName);
        JRRenderable renderer = image.getRenderer();
        if (renderer.getType() == 1) {
            renderer = new JRWrappingSvgRenderer(renderer, new Dimension(image.getWidth(), image.getHeight()), ModeEnum.OPAQUE == image.getModeValue() ? image.getBackcolor() : null);
        }
        imageMimeType = JRTypeSniffer.getImageMimeType(renderer.getImageType());
        try {
            imageData = renderer.getImageData();
        }
        catch (JRException e) {
            throw new ServletException((Throwable)e);
        }
        if (imageData != null && imageData.length > 0) {
            if (imageMimeType != null) {
                response.setHeader("Content-Type", imageMimeType);
            }
            response.setContentLength(imageData.length);
            ServletOutputStream ouputStream = response.getOutputStream();
            ouputStream.write(imageData, 0, imageData.length);
            ouputStream.flush();
            ouputStream.close();
        }
    }
}

