package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.ApiException;
import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnara.kecakplugins.jasperreports.utils.DataListJasperMixin;
import com.kinnarastudio.commons.Try;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.type.ImageTypeEnum;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;
import net.sf.jasperreports.renderers.SimpleDataRenderer;
import net.sf.jasperreports.web.util.WebHtmlResourceHandler;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewCategory;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;
import org.springframework.beans.BeansException;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 * Requires changes in core version 7635059fff56091b95948e4b314f989a06fbb51e
 *
 */
public class DataListJasperMenu extends UserviewMenu implements DataListJasperMixin, PluginWebSupport {
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map properties) {
        return null;
    }

    @Override
    public String getCategory() {
        return "Kecak";
    }

    @Override
    public String getIcon() {
        return "/plugin/" + getClassName() + "/images/grid_icon.gif";
    }

    @Override
    public String getRenderPage() {
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String menuId = ifEmpty(getPropertyCustomId(this), getPropertyId(this));
            String reportUrl = "/web/json/app/" + appDef.getAppId() + "/" + appDef.getVersion() + "/plugin/" + getClassName() + "/service?action=report&userviewId=" + this.getUserview().getPropertyString("id") + "&menuId=" + menuId;
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
            String body = generateHtmlBody();
            String result = header + body + footer;
            return result;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public String getJspPage() {
        return null;
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public String getLabel() {
        return "DataList Jasper";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{getClassName()};
        String json = AppUtil.readPluginResource(getClassName(), "/properties/dataListJasperReports.json", arguments, true, "message/jasperReports");
        return json;
    }

    /**
     * Web Service to download PDF or Excel
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LogUtil.info(getClass().getName(), "Executing JSON Rest API [" + request.getRequestURI() + "] in method [" + request.getMethod() + "] as [" + WorkflowUtil.getCurrentUsername() + "]");

        try {
            String action = getRequiredParameter(request, "action");
            if ("rows".equals(action)) {
                boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN);
                if (!isAdmin) {
                    throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "User [" + WorkflowUtil.getCurrentUsername() + "] is not admin");
                }

                String dataListId = getRequiredParameter(request, "dataListId");

                Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                        .map(m -> (Map<String, String[]>) m)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                JSONObject jsonResult = getDataListRow(dataListId, filters);
                response.getWriter().write(jsonResult.toString());

                return;
            }

            // get json url
            else if ("getJsonUrl".equals(action)) {
                final String dataListId = getRequiredParameter(request, "dataListId");

                final JSONObject jsonObject = new JSONObject();
                jsonObject.put("message", request.getRequestURL() + "?action=rows&dataListId=" + dataListId);

                response.getWriter().write(jsonObject.toString());
                return;
            }

            // report
            else if ("report".equals(action)) {
                final String userviewId = getRequiredParameter(request, "userviewId");
                final String key = getRequiredParameter(request, "key");
                final String menuId = getRequiredParameter(request, "menuId");
                final String type = getRequiredParameter(request, "type");
                final String json = getOptionalParameter(request, "json", "");
                final String contextPath = request.getContextPath();
                final Map parameterMap = request.getParameterMap();

                final AppDefinition appDef = AppUtil.getCurrentAppDefinition();

                final UserviewMenu selectedMenu = Optional.of(json)
                        .map(String::trim)
                        .filter(not(String::isEmpty))
                        .map(Try.onFunction(s -> findUserviewMenuFromPreview(s, menuId, contextPath, parameterMap, key)))
                        .orElse(Optional.ofNullable(findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath, parameterMap))
                                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Menu [" + menuId + "] is not available in userview [" + userviewId + "]")));

                generateReport(selectedMenu, type, request, response);

                return;
            }

            // load image
            else if("image".equals(action)) {
                final String imageName = getRequiredParameter(request, "image").trim();
                if ( !imageName.isEmpty( )) {
                    generateImage(request, response, imageName);
                    return;
                }
            }

            // unknown action
            else {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Invalid action [" + action + "]");
            }

            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (ApiException ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
            response.sendError(ex.getErrorCode(), ex.getMessage());

        } catch (Exception ex) {
            LogUtil.error(this.getClass().getName(), ex, ex.getMessage());

            HashMap<String, Object> model = new HashMap<>();
            model.put("request", request);
            model.put("exception", ex);
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/jasperError.ftl", null);
            response.setContentType("text/html");
            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    protected UserviewMenu findUserviewMenuFromPreview(String json, String menuId, String contextPath, Map parameterMap, String key) throws BeansException, KecakJasperException {
        UserviewService userviewService = (UserviewService) AppUtil.getApplicationContext().getBean("userviewService");
        Userview userview = userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key, Boolean.valueOf(true));
        UserviewMenu selectedMenu = findUserviewMenuInUserview(userview, menuId);
        return selectedMenu;
    }

    protected UserviewMenu findUserviewMenuFromDef(AppDefinition appDef, String userviewId, String menuId, String key, String contextPath, Map parameterMap) throws BeansException, KecakJasperException {
        UserviewService userviewService = (UserviewService) AppUtil.getApplicationContext().getBean("userviewService");
        UserviewDefinitionDao userviewDefinitionDao = (UserviewDefinitionDao) AppUtil.getApplicationContext().getBean("userviewDefinitionDao");

        return Optional.of(userviewId)
                .map(s -> userviewDefinitionDao.loadById(s, appDef))
                .map(UserviewDefinition::getJson)
                .map(json -> userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key, true))
                .map(tryFunction(u -> findUserviewMenuInUserview(u, menuId)))
                .orElseThrow(() -> new KecakJasperException("Error generating userview [" + userviewId + "]"));
    }

    protected UserviewMenu findUserviewMenuInUserview(Userview userview, String menuId) throws KecakJasperException {
        return getMenuStream(userview)
                .filter(u -> menuId.equals(getPropertyCustomId(u)) || menuId.equals(getPropertyId(u)))
                .findFirst()
                .orElseThrow(() -> new KecakJasperException("No matching menu [" + menuId + "] found in userview ["+userview.getPropertyString("id")+"]"));
    }

    /**
     * Get stream of userview menu
     *
     * @param userview
     * @return
     */
    private Stream<UserviewMenu> getMenuStream(Userview userview) {
        return Optional.ofNullable(userview)
                .map(Userview::getCategories)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(UserviewCategory::getMenus)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    protected String generateHtmlBody() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JasperPrint print = getJasperPrint(this, null);
            HtmlExporter jrHtmlExporter = new HtmlExporter();
            ExporterInput exporterInput = SimpleExporterInput.getInstance(Collections.singletonList(print));
            jrHtmlExporter.setExporterInput(exporterInput);

            SimpleHtmlExporterOutput exporterOutput = new SimpleHtmlExporterOutput(output, "UTF-8");
            exporterOutput.setImageHandler(new WebHtmlResourceHandler(AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?action=image&image={0}"));

            jrHtmlExporter.setExporterOutput(exporterOutput);

            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null) {
                request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
            }

            jrHtmlExporter.exportReport();
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            HashMap<String, Exception> model = new HashMap<>();
            model.put("exception", e);
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }
    }

    protected void generateReport(@Nonnull UserviewMenu menu, String type, HttpServletRequest request, HttpServletResponse response) throws Exception, IOException, JRException, BeansException, UnsupportedEncodingException, SQLException {
        String fileName = getFileName(menu);
        JasperPrint print = getJasperPrint(menu, null);
        OutputStream output = response.getOutputStream();
        if ("pdf".equals(type)) {
            response.setHeader("Content-Type", "application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=" + fileName + ".pdf");
            LogUtil.info(this.getClassName(), ("Generating PDF report for " + fileName));
            LogUtil.debug(this.getClassName(), ("Generating PDF report for " + fileName));
            JasperExportManager.exportReportToPdfStream(print, output);
        } else if ("xls".equals(type)) {
            response.setHeader("Content-Type", "application/vnd.ms-excel");
            response.setHeader("Content-Disposition", "inline; filename=" + fileName + ".xls");
            LogUtil.debug(this.getClass().getName(), ("Generating XLS report for " + fileName));

            JRXlsExporter exporter = new JRXlsExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));

            SimpleXlsReportConfiguration configuration = new SimpleXlsReportConfiguration();
            configuration.setOnePagePerSheet(true);
            configuration.setDetectCellType(true);
            configuration.setCollapseRowSpan(false);
            configuration.setWhitePageBackground(false);
            exporter.setConfiguration(configuration);
            exporter.exportReport();
        } else {
            response.setHeader("Content-Type", "text/html; charset=UTF-8");
            response.setHeader("Content-Disposition", "inline; filename=" + fileName + ".html");
            LogUtil.debug(this.getClass().getName(), ("Generating HTML report for " + fileName));

            if (request != null) {
                request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
            }

            HtmlExporter htmlExporter = new HtmlExporter();
            htmlExporter.setExporterInput(new SimpleExporterInput(print));

            { // set exporter output
                SimpleHtmlExporterOutput exporterOutput = new SimpleHtmlExporterOutput(output);
                { // set image handler
                    String imagesUriPattern = AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?image={0}";
//                    String imagesUriPattern = "{0}";
                    WebHtmlResourceHandler resourceHandler = new WebHtmlResourceHandler(imagesUriPattern);
                    exporterOutput.setImageHandler(resourceHandler);
                }
                htmlExporter.setExporterOutput(exporterOutput);
            }

            { // set configuration
                SimpleHtmlExporterConfiguration configuration = new SimpleHtmlExporterConfiguration();
                configuration.setHtmlHeader(getCustomHeader(menu));
                configuration.setHtmlFooter(getCustomFooter(menu));
                htmlExporter.setConfiguration(configuration);
            }

            htmlExporter.exportReport();
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Get menu ID
     *
     * @param menu
     * @return
     */
    private String getFileName(PropertyEditable menu) {
        return ifEmpty(ifEmpty(getPropertyFileName(menu), getPropertyCustomId(menu)), getPropertyId(menu));
    }
}

