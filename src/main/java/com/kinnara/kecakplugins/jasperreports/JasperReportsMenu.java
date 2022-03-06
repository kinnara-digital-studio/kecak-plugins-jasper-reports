package com.kinnara.kecakplugins.jasperreports;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.type.ImageTypeEnum;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewCategory;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.springframework.beans.BeansException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@Deprecated
public class JasperReportsMenu extends UserviewMenu implements PluginWebSupport {
    public String getName() {
        return getLabel() + getVersion();
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    public Object execute(Map properties) {
        return null;
    }

    public String getCategory() {
        return "Kecak";
    }

    public String getIcon() {
        return "/plugin/" + getClassName() + "/images/grid_icon.gif";
    }

    public String getRenderPage() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String menuId = this.getPropertyString("customId");
            if (menuId == null || menuId.trim().isEmpty()) {
                menuId = this.getPropertyString("id");
            }
            String reportUrl = "/web/json/plugin/" + getClassName() + "/service?action=report&appId=" + appDef.getId() + "&appVersion=" + appDef.getVersion() + "&userviewId=" + this.getUserview().getPropertyString("id") + "&menuId=" + menuId;
            if (!"true".equals(this.getRequestParameter("isPreview"))) {
                for (String key : ((Map<String, String>) this.getRequestParameters()).keySet()) {
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
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
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
        return "(Deprecated) JDBC Jasper";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{JasperReportsJdbcOptions.class.getName()};
        String json = AppUtil.readPluginResource(this.getClass().getName(), "/properties/jasperReports.json", arguments, true, "message/jasperReports");
        return json;
    }

    /**
     * Web Service to download PDF or Excel
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        try {
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
	                    if (appId != null) {
                                AppDefinitionDao appDefDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
	                        appDef = appDefDao.findLatestVersions(null, appId, null, null, null, null, null).iterator().next();
	                    }
	                    selectedMenu = json != null && !json.trim().isEmpty() ? findUserviewMenuFromPreview(json, menuId, contextPath, parameterMap, key) : this.findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath, parameterMap);
	                    if (selectedMenu != null) {
	                        this.generateReport(selectedMenu, type, output, request, response);
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
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
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

        JasperReport report = null;
        try {
            report = JasperCompileManager.compileReport(input);
        } catch (JRRuntimeException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            for(Object arg : e.getArgs()) {
                LogUtil.info(getClassName(), arg.toString());
            }
        }

        if(report == null)
            return null;

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
                hm.put("REPORT_VIRTUALIZER", virtualizer);
            }

            try(Connection conn = ds.getConnection()) {
                JasperPrint print = JasperFillManager.fillReport(report, hm, conn);
                return print;
            }
        }
        return null;
    }

    protected String generateReport() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            JasperPrint print = getReport(this);
            if (print != null) {
            	String menuId = this.getPropertyString("fileName").isEmpty()? 
                		this.getPropertyString("customId") : this.getPropertyString("fileName");
                if (menuId == null || menuId.trim().isEmpty()) {
                    menuId = this.getPropertyString("id");
                }
                LogUtil.debug(this.getClass().getName(), ("Generating HTML report for " + menuId));
                JRExporter jrHtmlExporter = new HtmlExporter();
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
            LogUtil.error(getClassName(), e, e.getMessage());
            HashMap<String, Exception> model = new HashMap<String, Exception>();
            model.put("exception", e);
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }
        return "";
    }

    protected void generateReport(UserviewMenu menu, String type, OutputStream output, HttpServletRequest request, HttpServletResponse response) throws Exception, IOException, JRException, BeansException, UnsupportedEncodingException, SQLException {
        JasperPrint print;
        String menuId = menu.getPropertyString("fileName").isEmpty()? 
        		menu.getPropertyString("customId") : menu.getPropertyString("fileName");
        if (menuId == null || menuId.trim().isEmpty()) {
            menuId = menu.getPropertyString("id");
        }
        if ((print = this.getReport(menu)) != null) {
            if ("pdf".equals(type)) {
                if (response != null) {
                    response.setHeader("Content-Type", "application/pdf");
                    response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".pdf");
                }
                LogUtil.info(this.getClass().getName(), ("Generating PDF report for " + menuId));
                LogUtil.debug(this.getClass().getName(), ("Generating PDF report for " + menuId));
                JasperExportManager.exportReportToPdfStream(print, output);
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
                JRExporter jrHtmlExporter = new HtmlExporter();
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
        String imageName = request.getParameter("image");
//        if ("px".equals(imageName)) {
//            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
//
//            try(InputStream input = pluginManager.getPluginResource(this.getClass().getName(), "net/sf/jasperreports/engine/images/pixel.GIF");) {
//                ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                byte[] bbuf = new byte[65536];
//                int length = 0;
//                while ((length = input.read(bbuf)) != -1) {
//                    stream.write(bbuf, 0, length);
//                }
//                byte[] imageData = stream.toByteArray();
//            } catch (Exception e) {
//                throw new ServletException(e);
//            }
//        }

        List<JasperPrint> jasperPrintList = BaseHttpServlet.getJasperPrintList((HttpServletRequest)request);
        if (jasperPrintList == null) {
            throw new ServletException("No JasperPrint documents found on the HTTP session.");
        }

        JRPrintImage image = HtmlExporter.getImage(jasperPrintList, imageName);
        JRRenderable renderer = image.getRenderable();
        try {
            byte[] imageData = renderer.getImageData();

            if (imageData != null && imageData.length > 0) {
                ImageTypeEnum mimeType = JRTypeSniffer.getImageTypeValue(imageData);
                response.setHeader("Content-Type", mimeType.getMimeType());
                response.setContentLength(imageData.length);
                ServletOutputStream ouputStream = response.getOutputStream();
                ouputStream.write(imageData, 0, imageData.length);
                ouputStream.flush();
                ouputStream.close();
            }
        } catch (JRException e) {
            e.printStackTrace();
        }
    }
}

