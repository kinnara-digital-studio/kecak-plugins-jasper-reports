package com.kinnara.kecakplugins.jasperreports.utils;

import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnara.kecakplugins.jasperreports.model.ReportSettings;
import com.kinnarastudio.commons.Declutter;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.type.ImageTypeEnum;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.engine.util.JRTypeSniffer;
import net.sf.jasperreports.j2ee.servlets.BaseHttpServlet;
import net.sf.jasperreports.renderers.SimpleDataRenderer;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.SetupManager;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.exception.ApiException;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface DataListJasperMixin extends Declutter {
    String PARAM_ACTION = "_action";
    String PARAM_DATALIST_ID = "_dataListId";
    String PARAM_USERVIEW_ID = "_userviewId";
    String PARAM_MENU_ID = "_menuId";
    String PARAM_KEY = "key";
    String PARAM_TYPE = "_type";
    String PARAM_JSON = "_json";
    String PARAM_IMAGE = "_image";
    String PARAM_ELEMENT_ID = "_elementId";
    String PARAM_FORM_ID = "_formId";
    String PARAM_ASSIGNMENT_ID = "_assignmentId";
    String PARAM_PROCESS_ID = "_processId";
    String PARAM_SORT = "_sort";
    String PARAM_DESC = "_desc";

    /**
     * Stream element children
     *
     * @param element
     * @return
     */
    @Nonnull
    default Stream<Element> elementStream(@Nonnull Element element, FormData formData) {
        if (!element.isAuthorize(formData)) {
            return Stream.empty();
        }

        Stream<Element> stream = Stream.of(element);
        for (Element child : element.getChildren()) {
            stream = Stream.concat(stream, elementStream(child, formData));
        }
        return stream;
    }

    @Nonnull
    default JasperPrint getJasperPrint(@Nonnull ExtDefaultPlugin prop, DataList dataList,
                                       WorkflowAssignment workflowAssignment, ReportSettings settings) throws KecakJasperException {
        String jrxml = settings.getJrxml();

        if (!JasperCompileManager.class.getClassLoader().equals(UserviewMenu.class.getClassLoader())) {
            jrxml = jrxml.replaceAll("language=\"groovy\"", "");
        }

        try (final ByteArrayInputStream input = new ByteArrayInputStream(jrxml.getBytes(StandardCharsets.UTF_8))) {
            final JasperReport report = JasperCompileManager.compileReport(input);
            final Map<String, Object> jasperParameters = getPropertyJasperParameter(prop, workflowAssignment);

            if (settings.isUseVirtualizer()) {
                final String path = SetupManager.getBaseDirectory() + "temp_jasper_swap";
                final File filepath = new File(path);
                if (!filepath.exists()) {
                    filepath.mkdirs();
                }
                final JRSwapFileVirtualizer virtualizer = new JRSwapFileVirtualizer(300,
                        new JRSwapFile(filepath.getAbsolutePath(), 4096, 100), true);
                jasperParameters.put("REPORT_VIRTUALIZER", virtualizer);
            }

            final String dataListId = getPropertyDataListId(prop, workflowAssignment);
            if (dataListId.isEmpty()) {
                final JRDataSource ds = new JREmptyDataSource();
                final JasperPrint print = JasperFillManager.fillReport(report, jasperParameters, ds);
                return print;
            } else {
                final Map<String, List<String>> filters = getPropertyDataListFilter(prop, dataList, report,
                        workflowAssignment);
                final JSONObject jsonResult = getDataListRow(dataListId, filters, settings.getSort(),
                        settings.isDesc());

                try (final InputStream inputStream = new ByteArrayInputStream(jsonResult.toString().getBytes())) {
                    final JRDataSource ds = new JsonDataSource(inputStream, "data");
                    final JasperPrint print = JasperFillManager.fillReport(report, jasperParameters, ds);
                    return print;
                }
            }
        } catch (IOException | JRException e) {
            throw new KecakJasperException("Error generating jasper", e);
        }
    }

    /**
     * Get property "fileName"
     *
     * @param menu
     * @return
     */
    default String getPropertyFileName(ExtDefaultPlugin menu) {
        return getOptionalProperty(menu, "filename");
    }

    /**
     * Get property "id"
     *
     * @param propertyEditable
     * @return
     */
    default String getPropertyId(ExtDefaultPlugin propertyEditable) {
        return propertyEditable.getPropertyString("id");
    }

    /**
     * Get property "customId"
     *
     * @param propertyEditable
     * @return
     */
    default String getPropertyCustomId(ExtDefaultPlugin propertyEditable) {
        return getOptionalProperty(propertyEditable, "customId");
    }

    default Form generateForm(String formDefId, final Map<String, Form> formCache) throws KecakJasperException {
        if (formCache.containsKey(formDefId)) {
            return formCache.get(formDefId);
        }

        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final ApplicationContext appContext = AppUtil.getApplicationContext();
        final FormService formService = (FormService) appContext.getBean("formService");
        final FormDefinitionDao formDefinitionDao = (FormDefinitionDao) appContext.getBean("formDefinitionDao");

        if (appDefinition == null) {
            throw new KecakJasperException("Application definition is not available");
        }

        final Form form = Optional.ofNullable(formDefId)
                .map(s -> formDefinitionDao.loadById(s, appDefinition))
                .map(FormDefinition::getJson)
                .map(formService::createElementFromJson)
                .map(e -> (Form) e)
                .orElseThrow(() -> new KecakJasperException("Error generating form [" + formDefId + "]"));

        formCache.put(formDefId, form);

        return form;
    }

    /**
     * Get property "jrxml"
     *
     * @param prop
     * @param workflowAssignment
     * @return
     */
    default String getPropertyJrxml(PropertyEditable prop, WorkflowAssignment workflowAssignment)
            throws KecakJasperException {
        return getRequiredProperty(prop, "jrxml", workflowAssignment);
    }

    /**
     * Get property "use_virtualizer"
     *
     * @param propertyEditable
     * @return
     */
    default boolean getPropertyUseVirtualizer(PropertyEditable propertyEditable) {
        return Optional.of("useVirtualizer")
                .map(propertyEditable::getPropertyString)
                .map("true"::equalsIgnoreCase)
                .orElse(false);
    }

    /**
     * Get property "dataListFilter"
     *
     * @param obj
     * @return
     */
    default Map<String, List<String>> getPropertyDataListFilter(ExtDefaultPlugin obj, DataList dataList,
            JasperReport jasperReport, WorkflowAssignment workflowAssignment) {
        final Map<String, List<String>> filters = new HashMap<>();
        // add filter from request parameter
        final DataListFilter[] dataListFilters = dataList.getFilters();
        Arrays.stream(dataListFilters)
                .filter(f -> f.getType() instanceof DataListFilterTypeDefault)
                .forEach(filter -> {
                    final DataListFilterTypeDefault filterType = (DataListFilterTypeDefault) filter.getType();

                    final String parameterName = filter.getName();
                    final String parameterValue = filterType.getValue(dataList, parameterName,
                            filterType.getPropertyString("defaultValue"));

                    if (parameterValue != null) {
                        if (filters.containsKey(parameterName)) {
                            filters.get(parameterName).add(parameterValue);
                        } else {
                            filters.put(parameterName, Collections.singletonList(parameterValue));
                        }
                    }
                });

        // add filter from properties
        Optional.of("dataListFilter")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .map(m -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(m.get("name"));
                    String value = Optional.of("value")
                            .map(m::get)
                            .map(String::valueOf)
                            .map(s -> processHashVariable(s, workflowAssignment))
                            .orElse("");

                    map.put(name, Collections.singletonList(value));
                    return map;
                })
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .forEach(e -> {
                    final String parameterName = e.getKey();
                    final List<String> parameterValue = e.getValue();
                    if (filters.containsKey(parameterName)) {
                        filters.get(parameterName).addAll(parameterValue);
                    } else {
                        filters.put(parameterName, parameterValue);
                    }
                });

        // add filter from jasper parameter
        final Map<String, Object> jasperParameter = getPropertyJasperParameter(obj, workflowAssignment);

        Optional.of(jasperReport)
                .map(JasperReport::getParameters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(jrp -> jasperParameter.containsKey(jrp.getName()))
                .filter(jrp -> Optional.of(jrp)
                        .map(JRParameter::getPropertiesMap)
                        .map(JRPropertiesMap::getPropertyNames)
                        .map(Arrays::stream)
                        .orElseGet(Stream::empty)
                        .anyMatch("net.sf.jasperreports.http.data.url.parameter"::equals))
                .forEach(jrParameter -> {
                    String parameterName = jrParameter.getName();
                    String parameterValue = Optional.of(parameterName)
                            .map(jasperParameter::get)
                            .map(String::valueOf)
                            .orElse("");

                    if (parameterValue.isEmpty()) {
                        return;
                    }

                    if (filters.containsKey(parameterName)) {
                        filters.get(parameterName).add(parameterValue);
                    } else {
                        filters.put(parameterName, new ArrayList<String>() {
                            {
                                add(parameterValue);
                            }
                        });
                    }
                });

        return filters;
    }

    /**
     * Get property "jasperParameter"
     *
     * @param obj
     * @return
     */
    default Map<String, Object> getPropertyJasperParameter(ExtDefaultPlugin obj, WorkflowAssignment assignment) {
        return Optional.of("jasperParameters")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .collect(Collectors.toMap(map -> String.valueOf(map.get("name")), it -> AppUtil
                        .processHashVariable(String.valueOf(it.getOrDefault("value", "")), assignment, null, null)));
    }

    /**
     * Get property "dataListId"
     *
     * @param prop
     * @return
     * @throws KecakJasperException
     */
    default String getPropertyDataListId(ExtDefaultPlugin prop, WorkflowAssignment assignment)
            throws KecakJasperException {
        return getOptionalProperty(prop, "dataListId", "");
    }

    /**
     * Get property as required
     *
     * @param prop
     * @param propertyName
     * @return
     * @throws KecakJasperException
     */
    default String getRequiredProperty(PropertyEditable prop, String propertyName,
            WorkflowAssignment workflowAssignment) throws KecakJasperException {
        return Optional.of(propertyName)
                .map(prop::getPropertyString)
                .map(s -> processHashVariable(s, workflowAssignment))
                .filter(not(String::isEmpty))
                .orElseThrow(() -> new KecakJasperException("Property [" + propertyName + "] is required"));
    }

    /**
     * @param prop
     * @param propertyName
     * @return
     */
    default String getOptionalProperty(ExtDefaultPlugin prop, String propertyName) {
        return getOptionalProperty(prop, propertyName, "");
    }

    /**
     * @param prop
     * @param propertyName
     * @param defaultValue
     * @return
     */
    default String getOptionalProperty(ExtDefaultPlugin prop, String propertyName, String defaultValue) {
        return Optional.of(propertyName)
                .map(prop::getPropertyString)
                .map(this::processHashVariable)
                .orElse(defaultValue);
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws KecakJasperException
     */
    @Nonnull
    default DataList getDataList(String datalistId) throws KecakJasperException {
        return getDataList(datalistId, null);
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws KecakJasperException
     */
    @Nullable
    default DataList getDataList(String datalistId, WorkflowAssignment workflowAssignment) throws KecakJasperException {
        if(datalistId.isEmpty()) {
            return null;
        }
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext
                .getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
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
    default JSONObject getDataListRow(String dataListId, @Nonnull final Map<String, List<String>> filters,
            @Nonnull String sort, boolean desc) throws KecakJasperException {
        final DataList dataList = getDataList(dataListId);
        getCollectFilters(dataList, filters);

        if (!sort.isEmpty()) {
            dataList.setDefaultSortColumn(sort);

            // order ASC / DESC
            dataList.setDefaultOrder(desc ? DataList.ORDER_DESCENDING_VALUE : DataList.ORDER_ASCENDING_VALUE);
        }

        final DataListCollection<Map<String, Object>> rows = dataList.getRows(DataList.MAXIMUM_PAGE_SIZE, 0);

        final JSONArray jsonArrayData = Optional.ofNullable(rows)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(m -> formatRow(dataList, m))
                .map(JSONObject::new)
                .collect(JSONCollectors.toJSONArray());

        JSONObject jsonResult = new JSONObject();
        try {
            jsonResult.put("data", jsonArrayData);
        } catch (JSONException e) {
            throw new KecakJasperException("Error retrieving data", e);
        }

        return jsonResult;
    }

    /**
     * @param content
     * @return
     */
    default String processHashVariable(String content) {
        return processHashVariable(content, null);
    }

    default void generateImage(HttpServletRequest request, HttpServletResponse response, String imageName)
            throws IOException, ServletException, ApiException {
        final List<JasperPrint> jasperPrintList = BaseHttpServlet.getJasperPrintList(request);
        if (jasperPrintList == null) {
            throw new ServletException("No JasperPrint documents found on the HTTP session.");
        }

        JRPrintImage image = HtmlExporter.getImage(jasperPrintList, imageName);
        SimpleDataRenderer dataRenderer = (SimpleDataRenderer) image.getRenderer();
        try {
            byte[] imageData = dataRenderer.getData(null);

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
     * @param content
     * @param assignment
     * @return
     */
    default String processHashVariable(Object content, WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(String.valueOf(content), assignment, null, null);
    }

    /**
     * Format Row
     *
     * @param dataList
     * @param row
     * @return
     */
    @Nonnull
    default Map<String, Object> formatRow(@Nonnull DataList dataList, @Nonnull final Map<String, Object> row) {
        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(not(DataListColumn::isHidden))
                .map(DataListColumn::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(s -> s, s -> formatValue(dataList, row, s)));
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
    default String formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, String field) {
        String value = Optional.of(field)
                .map(row::get)
                .map(String::valueOf)
                .orElse("");

        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(c -> field.equals(c.getName()))
                .findFirst()
                .map(column -> Optional.of(column)
                        .map(Try.onFunction(DataListColumn::getFormats))
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(f -> f.format(dataList, column, row, value))
                        .map(s -> s.replaceAll("<[^>]*>", ""))
                        .orElse(value))
                .orElse(value);
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    default void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);
    }

    /**
     * Get custom header
     *
     * @param propertyEditable
     * @return
     */
    default String getCustomHeader(@Nonnull PropertyEditable propertyEditable) {
        return Optional.of("customHeader")
                .map(propertyEditable::getPropertyString)
                .orElse("");
    }

    /**
     * Get custom footer
     *
     * @param propertyEditable
     * @return
     */
    default String getCustomFooter(@Nonnull PropertyEditable propertyEditable) {
        return Optional.of("customFooter")
                .map(propertyEditable::getPropertyString)
                .orElse("");
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
    default String getParameter(@Nonnull HttpServletRequest request, @Nonnull String parameterName)
            throws ApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(not(String::isEmpty))
                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST,
                        "Missing required parameter [" + parameterName + "]"));
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
    default String optParameter(@Nonnull HttpServletRequest request, @Nonnull String parameterName,
                                @Nonnull String defaultValue) {
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
    default boolean isEmpty(Object value) {
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
    default boolean isNotEmpty(Object value) {
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
    default <T, U extends T> T ifEmpty(T value, U failover) {
        return isEmpty(value) ? failover : value;
    }

    /**
     * Predicate not
     *
     * @param p
     * @param <T>
     * @return
     */
    default <T> Predicate<T> not(Predicate<T> p) {
        return (t) -> !p.test(t);
    }
}
