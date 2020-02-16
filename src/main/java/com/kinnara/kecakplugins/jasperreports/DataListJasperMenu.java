package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.KecakReportException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.type.ImageTypeEnum;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.service.AppService;
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
import org.json.JSONObject;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataListJasperMenu extends UserviewMenu implements PluginWebSupport {
    public String getName() {
        return "DataList Jasper";
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
            String menuId = this.getPropertyString("customId");
            if (menuId == null || menuId.trim().isEmpty()) {
                menuId = this.getPropertyString("id");
            }
            String reportUrl = "/web/json/app/"+appDef.getAppId()+"/"+appDef.getVersion()+"/plugin/" + getClassName() + "/service?action=report&userviewId=" + this.getUserview().getPropertyString("id") + "&menuId=" + menuId;
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
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{ getClassName() };
        String json = AppUtil.readPluginResource(getClassName(), "/properties/dataListJasperReports.json", arguments, true, "message/jasperReports");
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
        LogUtil.info(getClassName(), "Executing web service");

        try {
            String action = request.getParameter("action");
            String imageName = request.getParameter("image");
            if("rows".equals(action) ) {
                boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN);
                if (!isAdmin) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User [" + WorkflowUtil.getCurrentUsername() + "] is not admin");
                    return;
                }

                String dataListId = request.getParameter("dataListId");

                Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                        .map(m -> (Map<String, String[]>)m)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .collect(HashMap::new, (result, entry) -> {
                            result.put(entry.getKey(), Arrays.asList(entry.getValue()));
                        }, Map::putAll);

                JSONArray jsonArray = getDataListRow(dataListId, filters);
                response.getWriter().write(jsonArray.toString());

                return;
            } else if("getJsonUrl".equals(action)) {
                String dataListId = request.getParameter("dataListId");
                StringBuilder url = new StringBuilder(request.getRequestURL());
                url.append("?action=rows&dataListId=").append(dataListId);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("message", url.toString());

                response.getWriter().write(jsonObject.toString());
                return;
            } else if ("report".equals(action)) {
                String userviewId = request.getParameter("userviewId");
                String key = request.getParameter("key");
                String menuId = request.getParameter("menuId");
                String type = request.getParameter("type");
                String contextPath = request.getContextPath();
                Map parameterMap = request.getParameterMap();
                String json = request.getParameter("json");

                AppDefinition appDef = AppUtil.getCurrentAppDefinition();

                UserviewMenu selectedMenu = Optional.ofNullable(json)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> findUserviewMenuFromPreview(s, menuId, contextPath, parameterMap, key))
                        .orElse(findUserviewMenuFromDef(appDef, userviewId, menuId, key, contextPath, parameterMap));

                if (selectedMenu != null) {
                    this.generateReport(selectedMenu, type, request, response);
                }

                return;
            }

            if (imageName != null && !imageName.trim().isEmpty()) {
                this.generateImage(request, response);
                return;
            }

            response.setStatus(204);
            return;
        } catch (Exception ex) {
            LogUtil.error(this.getClass().getName(), (Throwable)ex, "");
            HashMap<String, Object> model = new HashMap<String, Object>();
            model.put("request", request);
            model.put("exception", ex);
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
            String content = pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
            response.setContentType("text/html");
            response.getOutputStream().write(content.getBytes("UTF-8"));
            return;
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

    protected JasperPrint getReport(@Nonnull UserviewMenu menu) throws JRException, UnsupportedEncodingException, KecakReportException, Exception {
        String jrxml = menu.getPropertyString("jrxml");
        if (!JasperCompileManager.class.getClassLoader().equals(UserviewMenu.class.getClassLoader())) {
            jrxml = jrxml.replaceAll("language=\"groovy\"", "");
        }

        JasperReport report;
        try(ByteArrayInputStream input = new ByteArrayInputStream(jrxml.getBytes("UTF-8"))) {
            report = JasperCompileManager.compileReport(input);
        }

        Map<String,Object> hm = Optional.ofNullable((Object[])menu.getProperty("parameters"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>)o)
                .collect(HashMap::new, (result, rowProperty) -> result.put(rowProperty.get("name").toString(), rowProperty.get("value")), Map::putAll);

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

        String dataListId = menu.getPropertyString("dataListId");
        Map<String, List<String>> filters = Optional.ofNullable((Object[])getProperty("dataListFilter"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>)o)
                .map(m -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(m.get("name"));
                    String value = Optional.ofNullable(m.get("value")).map(String::valueOf).map(s -> AppUtil.processHashVariable(s, null, null, null)).orElse("");

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

        JSONArray jsonResult = getDataListRow(dataListId, filters);
        LogUtil.info(getClassName(), "jsonResult ["+jsonResult.toString()+"]");
        JsonDataSource ds = new JsonDataSource(new ByteArrayInputStream(jsonResult.toString().getBytes()));
        JasperPrint print = JasperFillManager.fillReport(report, hm, ds);
        return print;
    }

    protected String generateReport() {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
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

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            HashMap<String, Exception> model = new HashMap<String, Exception>();
            model.put("exception", e);
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, this.getClass().getName(), "/templates/jasperError.ftl", null);
        }
        return "";
    }

    protected void generateReport(UserviewMenu menu, String type, HttpServletRequest request, HttpServletResponse response) throws Exception, IOException, JRException, BeansException, UnsupportedEncodingException, SQLException {
        JasperPrint print;
        String menuId = menu.getPropertyString("fileName").isEmpty()?
                menu.getPropertyString("customId") : menu.getPropertyString("fileName");
        if (menuId == null || menuId.trim().isEmpty()) {
            menuId = menu.getPropertyString("id");
        }
        if ((print = this.getReport(menu)) != null) {
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
            e.printStackTrace();
        }
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws KecakReportException
     */
    private DataList getDataList(String datalistId) throws KecakReportException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppService appService = (AppService) appContext.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition    = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(dataListService::fromJson)
                .orElseThrow(() -> new KecakReportException("DataList [" + datalistId + "] not found"));
    }


    /**
     * Get DataList row as JSONObject
     *
     * @param dataListId
     * @return
     */
    private JSONArray getDataListRow(String dataListId, @Nonnull final Map<String, List<String>> filters) throws KecakReportException {

        DataList dataList = getDataList(dataListId);
        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if(rows == null) {
            throw new KecakReportException("Error retrieving row from dataList ["+dataListId+"]");
        }

        JSONArray jsonArrayData = rows.stream()
                .map(m -> formatRow(dataList, m))
                .map(JSONObject::new)
                .collect(JSONArray::new, JSONArray::put, (arr1, arr2) -> {
                    for(int i = 0, size = arr2.length(); i < size; i++) {
                        arr1.put(arr2.optJSONObject(i));
                    }
                });

        return jsonArrayData;
    }

    /**
     * @param dataList          Input/Output parameter
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
}

