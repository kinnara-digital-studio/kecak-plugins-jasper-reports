package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.ApiException;
import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.type.ImageTypeEnum;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.export.ExporterInput;
import net.sf.jasperreports.export.HtmlExporterOutput;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.datalist.service.DataListService;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataListJasperMenu extends UserviewMenu implements PluginWebSupport {
    public String getName() {
        return getClass().getName();
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
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

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
            String body = this.generateReport();
            String result = header + body + footer;
            return result;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return e.getMessage();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
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
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                JSONObject jsonResult = getDataListRow(dataListId, filters);
                LogUtil.info(getClassName(), "webService : DataList result [" + jsonResult + "]");
                response.getWriter().write(jsonResult.toString());

                return;
            } else if ("getJsonUrl".equals(action)) {
                String dataListId = getRequiredParameter(request, "dataListId");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("message", request.getRequestURL() + "?action=rows&dataListId=" + dataListId);

                response.getWriter().write(jsonObject.toString());
                return;
            } else if ("report".equals(action)) {
                String userviewId = getRequiredParameter(request, "userviewId");
                String key = getRequiredParameter(request, "key");
                String menuId = getRequiredParameter(request, "menuId");
                String type = getRequiredParameter(request, "type");
                String json = getOptionalParameter(request, "json", "");
                String contextPath = request.getContextPath();
                Map parameterMap = request.getParameterMap();

                AppDefinition appDef = AppUtil.getCurrentAppDefinition();

                UserviewMenu selectedMenu = Optional.of(json)
                        .map(String::trim)
                        .filter(not(String::isEmpty))
                        .map(throwableFunction(s -> findUserviewMenuFromPreview(s, menuId, contextPath, parameterMap, key)))
                        .orElse(Optional.ofNullable(findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath, parameterMap))
                                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Menu [" + menuId + "] is not available in userview [" + userviewId + "]")));

                generateReport(selectedMenu, type, request, response);

                return;
            } else if("image".equals(action)) {
                String imageName = getOptionalParameter(request, "image", "").trim();
                if ( !imageName.isEmpty( )) {
                    generateImage(request, response, imageName);
                    return;
                }
            } else {
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
        UserviewMenu selectedMenu = null;
        UserviewService userviewService = (UserviewService) AppUtil.getApplicationContext().getBean("userviewService");
        UserviewDefinitionDao userviewDefinitionDao = (UserviewDefinitionDao) AppUtil.getApplicationContext().getBean("userviewDefinitionDao");
        UserviewDefinition userviewDef = userviewDefinitionDao.loadById(userviewId, appDef);
        if (userviewDef != null) {
            String json = userviewDef.getJson();
            Userview userview = userviewService.createUserview(json, menuId, false, contextPath, parameterMap, key, true);
            selectedMenu = findUserviewMenuInUserview(userview, menuId);
        }
        return selectedMenu;
    }

    protected UserviewMenu findUserviewMenuInUserview(Userview userview, String menuId) throws KecakJasperException {
        return getMenuStream(userview)
                .filter(it -> menuId.equals(getPropertyCustomId(it)) || menuId.equals(getPropertyId(it)))
                .findFirst()
                .orElseThrow(() -> new KecakJasperException("No matching menu found"));
//        UserviewMenu selectedMenu = null;
//        boolean found = false;
//        Collection<UserviewCategory> categories = userview.getCategories();
//        for (UserviewCategory category : categories) {
//            Collection<UserviewMenu> menus = category.getMenus();
//            for (UserviewMenu menu : menus) {
//                if (!menuId.equals(getPropertyCustomId(menu)) && !menuId.equals(getPropertyId(menu)))
//                    continue;
//                selectedMenu = menu;
//                found = true;
//                break;
//            }
//
//            if(found)
//                break;
//        }

//        return selectedMenu;
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
                .map(UserviewCategory::getMenus)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    protected JasperPrint getReport(@Nonnull UserviewMenu menu) throws JRException, UnsupportedEncodingException, KecakJasperException, Exception {
        String jrxml = getPropertyJrxml(menu);

        if (!JasperCompileManager.class.getClassLoader().equals(UserviewMenu.class.getClassLoader())) {
            jrxml = jrxml.replaceAll("language=\"groovy\"", "");
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8))) {

            JasperReport report = JasperCompileManager.compileReport(input);
            Map<String, Object> jasperParameters = getPropertyJasperParameter(menu);

            if (getPropertyUseVirtualizer(menu)) {
                String path = SetupManager.getBaseDirectory() + "temp_jasper_swap";
                File filepath = new File(path);
                if (!filepath.exists()) {
                    filepath.mkdirs();
                }
                JRSwapFileVirtualizer virtualizer = new JRSwapFileVirtualizer(300, new JRSwapFile(filepath.getAbsolutePath(), 4096, 100), true);
                jasperParameters.put("REPORT_VIRTUALIZER", virtualizer);
            }

            String dataListId = getPropertyDataListId(menu);

            JSONObject jsonResult;
            if (getPropertyUseRestApiDriver(menu)) {
                jsonResult = getDataFromApi(menu, dataListId);
            } else {
                Map<String, List<String>> filters = getPropertyDataListFilter(menu, report);
                jsonResult = getDataListRow(dataListId, filters);
            }

            try (InputStream inputStream = new ByteArrayInputStream(jsonResult.toString().getBytes())) {
                JsonDataSource ds = new JsonDataSource(inputStream, "data");
                JasperPrint print = JasperFillManager.fillReport(report, jasperParameters, ds);
                return print;
            }
        }
    }

    /**
     * Get property "use_virtualizer"
     *
     * @param menu
     * @return
     */
    private boolean getPropertyUseVirtualizer(UserviewMenu menu) {
        return Optional.of("use_virtualizer")
                .map(menu::getPropertyString)
                .map("true"::equalsIgnoreCase)
                .orElse(false);
    }

    /**
     * Get property as required
     *
     * @param menu
     * @param propertyName
     * @return
     * @throws KecakJasperException
     */
    private String getRequiredProperty(UserviewMenu menu, String propertyName) throws KecakJasperException {
        return Optional.of(propertyName)
                .map(menu::getPropertyString)
                .map(this::processHashVariable)
                .filter(not(String::isEmpty))
                .orElseThrow(() -> new KecakJasperException("Property [" + propertyName + "] is required"));
    }

    /**
     *
     * @param menu
     * @param propertyName
     * @return
     */
    private String getOptionalProperty(UserviewMenu menu, String propertyName) {
       return getOptionalProperty( menu, propertyName, "");
    }

    /**
     *
     * @param menu
     * @param propertyName
     * @param defaultValue
     * @return
     */
    private String getOptionalProperty(UserviewMenu menu, String propertyName, String defaultValue) {
        return Optional.of(propertyName)
                .map(menu::getPropertyString)
                .map(this::processHashVariable)
                .orElse(defaultValue);
    }

    /**
     * Get property "dataListId"
     *
     * @param menu
     * @return
     * @throws KecakJasperException
     */
    private String getPropertyDataListId(UserviewMenu menu) throws KecakJasperException {
        return getRequiredProperty(menu, "dataListId");
    }

    /**
     * Get property "userRestApiDriver"
     *
     * @param menu
     * @return
     */
    private boolean getPropertyUseRestApiDriver(UserviewMenu menu) {
        return "true".equalsIgnoreCase(getOptionalProperty(menu, "useRestApiDriver"));
    }

    /**
     * Get property "jasperParameter"
     *
     * @param menu
     * @return
     */
    private Map<String, Object> getPropertyJasperParameter(UserviewMenu menu) {
        return Optional.of("jasperParameters")
                .map(menu::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(it -> (Map<String, Object>) it)
                .collect(Collectors.toMap(it -> String.valueOf(it.get("name")), it -> AppUtil.processHashVariable(String.valueOf(it.getOrDefault("value", "")), null, null, null)));
    }

    private String processHashVariable(Object content) {
        return AppUtil.processHashVariable(String.valueOf(content), null, null, null);
    }

    /**
     * @param menu
     * @param dataListId
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws JSONException
     */
    @Nonnull
    private JSONObject getDataFromApi(UserviewMenu menu, String dataListId) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException, JSONException {
        // ignore any certificate
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true).build();

        HttpClient client = HttpClients.custom().setSSLContext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .build();

        String url = getPropertyUrl(menu) + "?action=rows&dataListId=" + dataListId + "&" + getPropertyUrlParameters(menu).entrySet().stream()
                .map(e -> e.getValue().stream()
                        .map(it -> e.getKey() + "=" + it)
                        .collect(Collectors.joining("&")))
                .collect(Collectors.joining("&"));

        final HttpRequestBase request = new HttpGet(url);
        getPropertyRequestHeaders(menu).forEach(request::addHeader);

        HttpResponse response = client.execute(request);

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            String responseBody = bufferedReader.lines().collect(Collectors.joining());
            return new JSONObject(responseBody);
        }
    }

    /**
     * Get property "jrxml"
     *
     * @param menu
     * @return
     * @throws KecakJasperException
     */
    private String getPropertyJrxml(UserviewMenu menu) throws KecakJasperException {
        return getRequiredProperty(menu, "jrxml");
    }

    /**
     * Get property "fileName"
     *
     * @param menu
     * @return
     */
    private String getPropertyFileName(UserviewMenu menu) {
        return menu.getPropertyString("fileName");
    }


    /**
     * Get property "id"
     *
     * @param menu
     * @return
     */
    private String getPropertyId(UserviewMenu menu) {
        return menu.getPropertyString("id");
    }

    /**
     * Get property "customId"
     *
     * @param menu
     * @return
     */
    private String getPropertyCustomId(UserviewMenu menu) {
        return menu.getPropertyString("customId");
    }

    /**
     * Get property "dataListFilter"
     *
     * @param menu
     * @return
     */
    private Map<String, List<String>> getPropertyDataListFilter(UserviewMenu menu, JasperReport jasperReport) {
        final Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(menu::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(it -> (Map<String, Object>) it)
                .map(it -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(it.get("name"));
                    String value = Optional.of("value")
                            .map(it::get)
                            .map(String::valueOf)
                            .map(this::processHashVariable)
                            .orElse("");

                    map.put(name, Collections.singletonList(value));
                    return map;
                })
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> {
                            List<String> result = new ArrayList<>(e1);
                            result.addAll(e2);
                            return result;
                        })
                );

        // add filter from jasper parameter
        Map<String, Object> jasperParameter = getPropertyJasperParameter(menu);

        Optional.of(jasperReport)
                .map(JasperReport::getParameters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(jrp -> jasperParameter.containsKey(jrp.getName()))
                .filter(jrp -> Optional.of(jrp)
                        .map(JRParameter::getPropertiesMap)
                        .map(JRPropertiesMap::getPropertyNames)
                        .map(Arrays::stream)
                        .orElseGet(Stream::empty)
                        .anyMatch("net.sf.jasperreports.http.data.url.parameter"::equals))
                .forEach(jrParameter -> {
                    String parameterName = jrParameter.getName();
                    String parameterValue = String.valueOf(jasperParameter.get(parameterName));
                    if(filters.containsKey(parameterName)) {
                        filters.get(parameterName).add(parameterValue);
                    } else {
                        filters.put(parameterName, Collections.singletonList(parameterValue));
                    }
                });

        return filters;
    }

    /**
     * Get property "url"
     *
     * @return
     */
    private String getPropertyUrl(UserviewMenu menu) {
        return processHashVariable(menu.getPropertyString("url"))
                .replaceAll("\\?.+$", "");
    }

    /**
     * Get property "requestHeaders"
     *
     * @return
     */
    @Nonnull
    private Map<String, String> getPropertyRequestHeaders(UserviewMenu menu) {
        return Optional.of("requestHeaders")
                .map(menu::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(it -> (Map<String, String>) it)
                .collect(Collectors.toMap(it -> processHashVariable(it.get("key")), it -> processHashVariable(it.get("value"))));
    }

    /**
     * Ger property "urlParameters"
     *
     * @return
     */
    private Map<String, List<String>> getPropertyUrlParameters(UserviewMenu menu) {
        return Optional.of("urlParameters")
                .map(menu::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(it -> (Map<String, String>) it)
                .collect(Collectors.toConcurrentMap(
                        it -> processHashVariable(it.get("key")),
                        it -> Arrays.asList(processHashVariable(it.get("value"))),
                        (strings, strings2) -> Stream.concat(strings.stream(), strings2.stream()).collect(Collectors.toList())));
    }

    protected String generateReport() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JasperPrint print = getReport(this);
            if (print != null) {
                HtmlExporter jrHtmlExporter = new HtmlExporter();
                ExporterInput exporterInput = SimpleExporterInput.getInstance(Collections.singletonList(print));
                jrHtmlExporter.setExporterInput(exporterInput);

                HtmlExporterOutput exporterOutput = new SimpleHtmlExporterOutput(output, "UTF-8");
                jrHtmlExporter.setExporterOutput(exporterOutput);

//                jrHtmlExporter.setParameter(JRHtmlExporterParameter.JASPER_PRINT, print);
                HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
                if (request != null) {
                    request.getSession().setAttribute("net.sf.jasperreports.j2ee.jasper_print", print);
                }
//                String imagesUri = AppUtil.getRequestContextPath() + "/web/json/plugin/" + getClassName() + "/service?action=image&image=";
//                jrHtmlExporter.setParameter(JRHtmlExporterParameter.IMAGES_URI, imagesUri);
                jrHtmlExporter.exportReport();
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            HashMap<String, Exception> model = new HashMap<String, Exception>();
            model.put("exception", e);
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }
        return "";
    }

    protected void generateReport(@Nonnull UserviewMenu menu, String type, HttpServletRequest request, HttpServletResponse response) throws Exception, IOException, JRException, BeansException, UnsupportedEncodingException, SQLException {
        String menuId = getMenuId(menu);
        JasperPrint print = getReport(menu);
        if (print != null) {
            OutputStream output = response.getOutputStream();
            if ("pdf".equals(type)) {
                response.setHeader("Content-Type", "application/pdf");
                response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".pdf");
                LogUtil.info(this.getClassName(), ("Generating PDF report for " + menuId));
                LogUtil.debug(this.getClassName(), ("Generating PDF report for " + menuId));
                JasperExportManager.exportReportToPdfStream(print, output);
            } else if ("xls".equals(type)) {
                response.setHeader("Content-Type", "application/vnd.ms-excel");
                response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".xls");
                LogUtil.debug(this.getClass().getName(), ("Generating XLS report for " + menuId));
                JRXlsExporter exporter = new JRXlsExporter();
                exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, output);
                exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
                exporter.setParameter(JRExporterParameter.CHARACTER_ENCODING, "UTF-8");
                exporter.exportReport();
            } else {
                response.setHeader("Content-Type", "text/html; charset=UTF-8");
                response.setHeader("Content-Disposition", "inline; filename=" + menuId + ".html");
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
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    protected void generateImage(HttpServletRequest request, HttpServletResponse response, String imageName) throws IOException, ServletException, ApiException {
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

        List<JasperPrint> jasperPrintList = BaseHttpServlet.getJasperPrintList(request);
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
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
        }
    }

    /**
     * Get menu ID
     *
     * @param menu
     * @return
     */
    private String getMenuId(UserviewMenu menu) {
        return ifEmpty(ifEmpty(getPropertyFileName(menu), getPropertyCustomId(menu)), getPropertyId(menu));
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws KecakJasperException
     */
    private DataList getDataList(String datalistId) throws KecakJasperException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(this::processHashVariable)
                .map(dataListService::fromJson)
                .orElseThrow(() -> new KecakJasperException("DataList [" + datalistId + "] not found"));
    }


    /**
     * Get DataList row as JSONObject
     *
     * @param dataListId
     * @return
     */

    @Nonnull
    private JSONObject getDataListRow(String dataListId, @Nonnull final Map<String, List<String>> filters) throws KecakJasperException {
        DataList dataList = getDataList(dataListId);

        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if (rows == null) {
            throw new KecakJasperException("Error retrieving row from dataList [" + dataListId + "]");
        }

        JSONArray jsonArrayData = rows.stream()
                .map(m -> formatRow(dataList, m))
                .map(JSONObject::new)
                .collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

        JSONObject jsonResult = new JSONObject();
        try {
            jsonResult.put("data", jsonArrayData);
        } catch (JSONException e) {
            throw new KecakJasperException("Error retrieving data", e);
        }

        return jsonResult;
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    private void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Arrays.stream(dataList.getFilters())
                .peek(f -> {
                    if (!(f.getType() instanceof DataListFilterTypeDefault))
                        LogUtil.warn(getClass().getName(), "DataList filter [" + f.getName() + "] is not instance of [" + DataListFilterTypeDefault.class.getName() + "], filter will be ignored");
                })
                .filter(f -> Objects.nonNull(filters.get(f.getName())) && f.getType() instanceof DataListFilterTypeDefault)
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));
    }

    /**
     * Format Row
     *
     * @param dataList
     * @param row
     * @return
     */
    @Nonnull
    private Map<String, Object> formatRow(DataList dataList, Map<String, Object> row) {
        Map<String, Object> formatterRow = new HashMap<>();
        for (DataListColumn column : dataList.getColumns()) {
            String field = column.getName();
            formatterRow.put(field, formatValue(dataList, row, field));
        }

        return formatterRow;
    }

    /**
     * Format
     *
     * @param dataList DataList
     * @param row      Row
     * @param field    Field
     * @return
     */
    @Nonnull
    private String formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, String field) {
        if (dataList.getColumns() == null) {
            return Optional.ofNullable(row.get(field))
                    .map(String::valueOf)
                    .orElse("");
        }

        for (DataListColumn column : dataList.getColumns()) {
            if (!field.equals(column.getName())) {
                continue;
            }

            String value = Optional.ofNullable(row.get(field))
                    .map(String::valueOf)
                    .orElse("");

            if (column.getFormats() == null) {
                return value;
            }

            for (DataListColumnFormat format : column.getFormats()) {
                if (format != null) {
                    return format.format(dataList, column, row, value).replaceAll("<[^>]*>", "");
                }
            }
        }

        return Optional.ofNullable(row.get(field)).map(String::valueOf).orElse("");
    }

    /**
     * Get required parameter, if not supplied, throw {@link ApiException}
     *
     * @param request
     * @param parameterName
     * @return
     * @throws ApiException
     */
    @Nonnull
    private String getRequiredParameter(@Nonnull HttpServletRequest request, @Nonnull String parameterName) throws ApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(not(String::isEmpty))
                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter [" + parameterName + "]"));
    }

    /**
     * Get optional parameter
     *
     * @param request
     * @param parameterName
     * @param defaultValue
     * @return
     */
    @Nonnull
    private String getOptionalParameter(@Nonnull HttpServletRequest request, @Nonnull String parameterName, @Nonnull String defaultValue) {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(not(String::isEmpty))
                .orElse(defaultValue);
    }

    /**
     * Is string representation of value empty
     *
     * @param value
     * @return
     */
    private boolean isEmpty(Object value) {
        return Optional.ofNullable(value)
                .map(String::valueOf)
                .map(String::trim)
                .map(String::isEmpty)
                .orElse(true);
    }

    /**
     * Is string representation of value not empty
     *
     * @param value
     * @return
     */
    private boolean isNotEmpty(Object value) {
        return !isEmpty(value);
    }

    /**
     * Return failover if value is null or empty
     *
     * @param value
     * @param failover
     * @param <T>
     * @param <U>
     * @return
     */
    private <T, U extends T> T ifEmpty(T value, U failover) {
        return isEmpty(value) ? failover : value;
    }


    /**
     * Predicate not
     *
     * @param p
     * @param <T>
     * @return
     */
    private <T> Predicate<T> not(Predicate<T> p) {
        return (t) -> !p.test(t);
    }

    /**
     * Throwable supplier
     *
     * @param throwableSupplier
     * @param <R>
     * @param <E>
     * @return
     */
    private <R, E extends Exception> ThrowableSupplier<R, E> throwableSupplier(ThrowableSupplier<R, E> throwableSupplier) {
        return throwableSupplier;
    }

    /**
     * Throwable function
     *
     * @param throwableFunction
     * @param <T>
     * @param <R>
     * @param <E>
     * @return
     */
    private <T, R, E extends Exception> ThrowableFunction<T, R, ? extends E> throwableFunction(ThrowableFunction<T, R, ? extends E> throwableFunction) {
        return throwableFunction;
    }

    @FunctionalInterface
    interface ThrowableSupplier<R, E extends Exception> extends Supplier<R> {
        @Nullable
        R getThrowable() throws E;

        @Nullable
        default R get() {
            try {
                return getThrowable();
            } catch (Exception e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
                return null;
            }
        }

        default ThrowableSupplier<R, E> onException(Function<? super E, R> onException) {
            try {
                return this::getThrowable;
            } catch (Exception e) {
                Objects.requireNonNull(onException);
                return () -> onException.apply((E) e);
            }
        }
    }

    /**
     * Throwable version of {@link Function}.
     * Returns null then exception is raised
     *
     * @param <T>
     * @param <R>
     * @param <E>
     */
    @FunctionalInterface
    interface ThrowableFunction<T, R, E extends Exception> extends Function<T, R> {

        @Override
        default R apply(T t) {
            try {
                return applyThrowable(t);
            } catch (Exception e) {
                LogUtil.error(getClass().getName(), e, e.getMessage());
                return null;
            }
        }

        R applyThrowable(T t) throws E;

        /**
         * @param f
         * @return
         */
        default Function<T, R> onException(Function<? super E, ? extends R> f) {
            return (T a) -> {
                try {
                    return (R) applyThrowable(a);
                } catch (Exception e) {
                    return f.apply((E) e);
                }
            };
        }

        /**
         * @param f
         * @return
         */
        default Function<T, R> onException(BiFunction<? super T, ? super E, ? extends R> f) {
            return (T a) -> {
                try {
                    return (R) applyThrowable(a);
                } catch (Exception e) {
                    return f.apply(a, (E) e);
                }
            };
        }
    }
}

