package com.kinnarastudio.kecakplugins.jasperreports;

import com.kinnarastudio.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnarastudio.kecakplugins.jasperreports.model.ReportSettings;
import com.kinnarastudio.kecakplugins.jasperreports.utils.DataListJasperMixin;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.web.util.WebHtmlResourceHandler;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.Userview;
import org.joget.apps.userview.model.UserviewCategory;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kecak.apps.exception.ApiException;
import org.springframework.beans.BeansException;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *         <p>
 *         Requires changes in core version
 *         7635059fff56091b95948e4b314f989a06fbb51e
 */
public class DataListJasperMenu extends UserviewMenu implements DataListJasperMixin, PluginWebSupport {
    public final static String LABEL = "DataList Jasper";

    public String getName() {
        return LABEL;
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
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
        return getRenderPage("/templates/DataListJasperMenu.ftl", "/templates/DataListJasperMenuPdf.ftl",
                "/templates/jasperError.ftl");
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
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{getClassName(), getClassName()};
        String json = AppUtil.readPluginResource(getClassName(), "/properties/dataListJasperReports.json", arguments, true, "messages/jasperReports");
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
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        LogUtil.info(getClass().getName(), "Executing JSON Rest API [" + request.getRequestURI() + "] in method ["
                + request.getMethod() + "] as [" + WorkflowUtil.getCurrentUsername() + "]");

        try {
            final String action = getParameter(request, PARAM_ACTION);
            switch (action) {
                case "rows": {
                    final int rows = optParameter(request, PARAM_ROWS)
                            .filter(s -> s.matches("\\d+"))
                            .map(Integer::parseInt)
                            .orElse(Integer.MAX_VALUE);

                    boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN);
                    if (!isAdmin) {
                        throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED,
                                "User [" + WorkflowUtil.getCurrentUsername() + "] is not admin");
                    }

                    final String dataListId = getParameter(request, PARAM_DATALIST_ID);

                    final Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                            .map(m -> (Map<String, String[]>) m)
                            .map(Map::entrySet)
                            .stream()
                            .flatMap(Collection::stream)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                    final String sort = getSortBy();
                    final boolean desc = isSortDescending();

                    final JSONObject jsonResult = getDataListRow(dataListId, filters, sort, desc, rows);
                    response.getWriter().write(jsonResult.toString());

                    return;
                }

                // get datalist fields
                case "fieldsOptions": {
                    final String dataListId = getParameter(request, "dataListId");
                    final DataList dataList = getDataList(dataListId);
                    final JSONArray jsonResponse = Optional.of(dataList)
                            .map(DataList::getBinder)
                            .map(DataListBinder::getColumns)
                            .stream()
                            .flatMap(Arrays::stream)
                            .filter(Objects::nonNull)
                            .map(Try.onFunction(c -> {
                                final JSONObject json = new JSONObject();
                                json.put(FormUtil.PROPERTY_VALUE, c.getName());
                                json.put(FormUtil.PROPERTY_LABEL, c.getLabel());
                                return json;
                            }))
                            .collect(JSONCollectors.toJSONArray());

                    response.getWriter().write(jsonResponse.toString());
                    return;
                }

                // get json url
                case "getJsonUrl": {
                    final String dataListId = optParameter(request, "dataListId", "");

                    final JSONObject jsonObject = new JSONObject() {{
                        final String message;
                        if(dataListId.isEmpty()) {
                            message = "";
                        } else {
                            message = request.getRequestURL() + "?action=rows&dataListId=" + dataListId;
                        }
                        put("message", message);
                    }};
                    response.getWriter().write(jsonObject.toString());
                    return;
                }

                // report
                case "report": {
                    final String userviewId = getParameter(request, PARAM_USERVIEW_ID);
                    final String key = getParameter(request, PARAM_KEY);
                    final String menuId = getParameter(request, PARAM_MENU_ID);
                    final String type = getParameter(request, PARAM_TYPE);
                    final String json = optParameter(request, PARAM_JSON, "");
                    final String contextPath = request.getContextPath();
                    final Map parameterMap = request.getParameterMap();
                    final String sort = optParameter(request, PARAM_SORT, "");
                    final boolean desc = "true".equalsIgnoreCase(optParameter(request, PARAM_DESC, ""));
                    final int rows = optParameter(request, PARAM_ROWS)
                            .filter(s -> s.matches("\\d+"))
                            .map(Integer::parseInt)
                            .orElse(Integer.MAX_VALUE);

                    final AppDefinition appDef = AppUtil.getCurrentAppDefinition();

                    final DataListJasperMenu selectedMenu = (DataListJasperMenu) Optional.of(json)
                            .map(String::trim)
                            .filter(not(String::isEmpty))
                            .map(Try.onFunction(
                                    s -> findUserviewMenuFromPreview(s, menuId, contextPath, parameterMap, key)))
                            .filter(m -> m instanceof DataListJasperMenu)
                            .orElse(Optional
                                    .ofNullable(findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath,
                                            parameterMap))
                                    .filter(m -> m instanceof DataListJasperMenu)
                                    .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Menu ["
                                            + menuId + "] is not available in userview [" + userviewId + "]")));

                    final boolean useVirtualizer = getPropertyUseVirtualizer(selectedMenu);
                    final String jrxml = getPropertyJrxml(selectedMenu, null);
                    final String dataListId = selectedMenu.getPropertyDataListId();
                    final DataList dataList = getDataList(dataListId);
                    final ReportSettings settings = new ReportSettings(sort, desc, rows, useVirtualizer, jrxml);
                    generateReport(selectedMenu, type, request, response, dataList, settings);

                    return;
                }

                // load image
                case "image":
                    final String imageName = getParameter(request, PARAM_IMAGE).trim();
                    if (!imageName.isEmpty()) {
                        generateImage(request, response, imageName);
                        return;
                    }
                    break;

                // unknown action
                default:
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
            String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(),
                    "/templates/jasperError.ftl", null);
            response.setContentType("text/html");
            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    protected UserviewMenu findUserviewMenuFromPreview(String json, String menuId, String contextPath, Map parameterMap,
            String key) throws BeansException, KecakJasperException {
        UserviewService userviewService = (UserviewService) AppUtil.getApplicationContext().getBean("userviewService");
        Userview userview = userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key,
                Boolean.valueOf(true));
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
                .map(Try.onFunction(u -> findUserviewMenuInUserview(u, menuId)))
                .orElseThrow(() -> new KecakJasperException("Error generating userview [" + userviewId + "]"));
    }

    protected UserviewMenu findUserviewMenuInUserview(Userview userview, String menuId) throws KecakJasperException {
        return getMenuStream(userview)
                .filter(u -> menuId.equals(getPropertyCustomId(u)) || menuId.equals(getPropertyId(u)))
                .findFirst()
                .orElseThrow(() -> new KecakJasperException("No matching menu [" + menuId + "] found in userview [" + userview.getPropertyString("id") + "]"));
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

    /**
     * @return
     */
    protected String generateHtmlBody(DataList dataList, ReportSettings setting) throws IOException, KecakJasperException, JRException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final JasperPrint print =
                    getJasperPrint(this, dataList, null, setting);
            final HtmlExporter jrHtmlExporter = new HtmlExporter();
            final ExporterInput exporterInput = SimpleExporterInput.getInstance(Collections.singletonList(print));
            jrHtmlExporter.setExporterInput(exporterInput);

            final SimpleHtmlExporterOutput exporterOutput = new SimpleHtmlExporterOutput(output, "UTF-8");
            exporterOutput.setImageHandler(new WebHtmlResourceHandler(AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?" + PARAM_ACTION + "=image&" + PARAM_IMAGE + "={0}"));

            jrHtmlExporter.setExporterOutput(exporterOutput);

            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null) {
                request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
            }

            jrHtmlExporter.exportReport();

            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    protected String generatePdfBody(String pdfTemplate, String userviewId, String menuId, ReportSettings settings) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final Map<String, Object> model = new HashMap<>();
        model.put("ratio", "4by3");
        model.put("src", "/web/json/app/" + appDefinition.getAppId() + "/" + appDefinition.getVersion() + "/plugin/" + getClassName() + "/service?_action=report&_menuId=" + menuId + "&key=_&_userviewId=" + userviewId + "&_type=pdf");
        return pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), pdfTemplate, null);
    }

    protected void generateReport(@Nonnull UserviewMenu menu, String type, HttpServletRequest request, HttpServletResponse response, DataList dataList, ReportSettings settings) throws JRException, BeansException, KecakJasperException {
        final String fileName = getFileName(menu);
        final JasperPrint print = getJasperPrint(menu, dataList, null, settings);

        try (final OutputStream output = response.getOutputStream()) {
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

                final JRXlsExporter exporter = new JRXlsExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));

                final SimpleXlsReportConfiguration configuration = new SimpleXlsReportConfiguration();
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

                final HtmlExporter htmlExporter = new HtmlExporter();
                htmlExporter.setExporterInput(new SimpleExporterInput(print));

                { // set exporter output
                    final SimpleHtmlExporterOutput exporterOutput = new SimpleHtmlExporterOutput(output);
                    { // set image handler
                        final String imagesUriPattern = AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?" + PARAM_IMAGE + "={0}";
                        final WebHtmlResourceHandler resourceHandler = new WebHtmlResourceHandler(imagesUriPattern);
                        exporterOutput.setImageHandler(resourceHandler);
                    }
                    htmlExporter.setExporterOutput(exporterOutput);
                }

                { // set configuration
                    final SimpleHtmlExporterConfiguration configuration = new SimpleHtmlExporterConfiguration();
                    configuration.setHtmlHeader(getCustomHeader(menu));
                    configuration.setHtmlFooter(getCustomFooter(menu));
                    htmlExporter.setConfiguration(configuration);
                }

                htmlExporter.exportReport();
            }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (IOException e) {
            throw new KecakJasperException(e);
        }
    }

    /**
     * Get menu ID
     *
     * @param menu
     * @return
     */
    protected String getFileName(ExtDefaultPlugin menu) {
        return ifEmpty(ifEmpty(getPropertyFileName(menu), getPropertyCustomId(menu)), getPropertyId(menu));
    }

    protected String getRenderPage(String template, String pdfTemplate, String errorTemplate) {
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        try {

            final String userviewId = this.getUserview().getPropertyString("id");
            final String menuId = ifEmpty(getPropertyCustomId(this), getPropertyId(this));
            String reportUrl = "/web/json/app/" + appDef.getAppId() + "/" + appDef.getVersion() + "/plugin/" + getClassName() + "/service?" + PARAM_ACTION + "=report&" + PARAM_USERVIEW_ID + "=" + userviewId + "&" + PARAM_MENU_ID + "=" + menuId;
            final boolean isPreview = "true".equals(getRequestParameter("isPreview"));
            if (!isPreview) {
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
            String pdfUrl = contextPath + reportUrl + "&" + PARAM_TYPE + "=pdf&" + PARAM_SORT + "=" + getSortBy() + "&" + PARAM_DESC + "=" + isSortDescending();
            String excelUrl = contextPath + reportUrl + "&" + PARAM_TYPE + "=xls&" + PARAM_SORT + "=" + getSortBy() + "&" + PARAM_DESC + "=" + isSortDescending();
            ;
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
            setProperty("includeFooter", footer);

            final Map<String, Object> model = new HashMap<>();
            model.put("showDataListFilter", "true".equalsIgnoreCase(getPropertyString("showDataListFilter")));

            final String dataListId = getPropertyString("dataListId");
            model.put("dataListId", dataListId);

            if (!dataListId.equals("")) {
                final DataList dataList = getDataList(dataListId);

                // filter template
                final List<String> filterTemplates = new ArrayList<>();

                Pattern pagePattern = Pattern.compile("id='d-[0-9]+-p'|id='d-[0-9]+-ps'");
                for (String filterTemplate : dataList.getFilterTemplates()) {
                    if (!pagePattern.matcher(filterTemplate).find()) {
                        filterTemplates.add(filterTemplate);
                    }
                }

                model.put("filterTemplates", filterTemplates);

                final String sort = getSortBy();
                final boolean desc = isSortDescending();
                final boolean useVirtualizer = getPropertyUseVirtualizer(this);
                final String jrxml = getPropertyJrxml(this, null);
                final ReportSettings setting = new ReportSettings(sort, desc, dataList.getSize(), useVirtualizer, jrxml);

                final String outputType = getPropertyString("output");
                final String jasperContent;

                // PDF
                if ("pdf".equalsIgnoreCase(outputType)) {
                    jasperContent = generatePdfBody(pdfTemplate, userviewId, menuId, setting);
                }

                // HTML
                else {
                    jasperContent = generateHtmlBody(dataList, setting);
                }
                model.put("jasperContent", jasperContent);
            } else {
                final String sort = getSortBy();
                final boolean desc = isSortDescending();
                final boolean useVirtualizer = getPropertyUseVirtualizer(this);
                final String jrxml = getPropertyJrxml(this, null);
                final ReportSettings setting = new ReportSettings(sort, desc, Integer.MAX_VALUE, useVirtualizer, jrxml);

                final String outputType = getPropertyString("output");
                final String jasperContent;
                // PDF
                if ("pdf".equalsIgnoreCase(outputType)) {
                    jasperContent = generatePdfBody(pdfTemplate, userviewId, menuId, setting);
                }

                // HTML
                else {
                    DataList emptyDataList = new DataList();
                    DataListCollection<String> emptyDataListCollectionRow = new DataListCollection<>();
                    emptyDataListCollectionRow.add("empty");
                    emptyDataList.setRows(emptyDataListCollectionRow);
                    jasperContent = generateHtmlBody(emptyDataList, setting);
                }
                model.put("jasperContent", jasperContent);
            }

            model.put("customHeader", header);
            model.put("customFooter", footer);

            String result = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), template, null);
            return result;

        } catch (
                Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            final Map<String, Object> model = Collections.singletonMap("exception", e);
            return pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), errorTemplate, null);
        }
    }

    protected String getSortBy() {
        return getPropertyString("dataListSortBy");
    }

    protected boolean isSortDescending() {
        return "true".equalsIgnoreCase(getPropertyString("dataListSortDescending"));
    }

    protected String getPropertyDataListId() {
        try {
            return getRequiredProperty(this, "dataListId", null);
        } catch (KecakJasperException e) {
            return "";
        }
    }
}

