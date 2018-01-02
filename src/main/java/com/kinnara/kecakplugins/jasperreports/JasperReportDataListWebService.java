package com.kinnara.kecakplugins.jasperreports;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class JasperReportDataListWebService extends ExtDefaultPlugin implements PluginWebSupport {
    private Map<String, DataList> datalistCache = new HashMap<>();

    @Override
    public String getName() {
        return "Jasper Report DataList Web Service";
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
    public Object execute(Map props) {
        return null;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        String appId = request.getParameter("appId");
        String appVersion = request.getParameter("appVersion");
        String dataListId = request.getParameter("dataListId");
        DataList dataList;

        if(!"GET".equals(method)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Use GET method");
        } else if(appId == null || appId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter appId not provided");
        } else if(appVersion == null || appVersion.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter appVersion not provided");
        } else if(dataListId == null || dataListId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parameter dataListId not provided");
        } else if(null != (dataList = getDataList(appId, appVersion, dataListId))) {
            getCollectFilters(((Map<String, Object>)request.getParameterMap()), dataList);

            DataListCollection<Map<String, String>> collections = dataList.getRows();
            JSONArray data        = new JSONArray();
            for(Map<String, String> row : collections) {
                try {
                    JSONObject jsonRow = new JSONObject();
                    for(String field : row.keySet()) {
                        String value = format(dataList, row, field);
                        if(value != null)
                            jsonRow.put(field, value);
                        else if(row.get(field) != null)
                            jsonRow.put(field, row.get(field));
                    }
                    data.put(jsonRow);
                } catch (JSONException e) {
                    data.put(new JSONObject(row));
                    LogUtil.error(getClass().getName(), e, "");
                }
            }
            response.getWriter().write(data.toString());
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "null DataList");
        }
    }

    /**
     * Format field based on DataList column formatter plugins
     * @param dataList
     * @param row
     * @param field
     * @return
     */
    private String format(DataList dataList, Map<String, String> row, String field) {
        if(dataList.getColumns() != null) {
            for(DataListColumn column : dataList.getColumns()) {
                if(field.equals(column.getName())) {
                    String value = String.valueOf(row.get(field));
                    if(column.getFormats() != null) {
                        for(DataListColumnFormat format : column.getFormats()) {
                            if(format != null) {
                                return format.format(dataList, column, row, value).replaceAll("<[^>]*>", "");
                            }
                        }
                    } else {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private DataList getDataList(String appId, String appVersion, String datalistId) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppService appService = (AppService) appContext.getBean("appService");
        AppDefinition appDef = appService.getAppDefinition(appId, appVersion);

        if (datalistCache.containsKey(datalistId))
            return datalistCache.get(datalistId);

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition    = datalistDefinitionDao.loadById(datalistId, appDef);
        if (datalistDefinition != null) {
            DataList dataList = dataListService.fromJson(datalistDefinition.getJson());
            datalistCache.put(datalistId, dataList);
            return dataList;
        }
        return null;
    }

    /**
     * Collect datalist filter
     * @param dataList
     * @param requestParameters
     */
    private void getCollectFilters(Map<String, Object> requestParameters, DataList dataList) {
        DataListFilter[] filters = dataList.getFilters();
        Comparator<DataListFilter> comparator = Comparator.comparing(DataListFilter::getName);

        Arrays.sort(filters, comparator);
        DataListFilter key = new DataListFilter();
        for(Map.Entry<String, Object> entry : requestParameters.entrySet()) {
            key.setName(entry.getKey());
            int index = Arrays.binarySearch(filters, key, comparator);
            if(index >= 0) {
                try {
                    // parameter is one of the filter
                    DataListFilterQueryObject filter = new DataListFilterQueryObject();
                    filter.setOperator("AND");
                    if(entry.getValue() instanceof String[]) {
                        StringBuilder sbOrCombination = new StringBuilder();
                        sbOrCombination.append("(");

                        String[] parameterValues = (String[])entry.getValue();
                        String[] values = new String[parameterValues.length];
                        for(int i = 0, size = parameterValues.length; i< size; i++) {
                            if(i > 0)
                                sbOrCombination.append(" OR ");
                            sbOrCombination.append("lower(").append(dataList.getBinder().getColumnName(entry.getKey())).append(") LIKE lower(?)");

                            values[i] = "%" + parameterValues[i] + "%";
                        }
                        sbOrCombination.append(")");

                        filter.setQuery(sbOrCombination.toString());
                        filter.setValues(values);
                    } else {
                        // this is the default pattern of datalist filter query is "lower([field]) like lower(?)"
                        filter.setQuery("lower(" + dataList.getBinder().getColumnName(entry.getKey()) + ") LIKE lower(?)");
                        filter.setValues( new String[] { "%" + entry.getValue().toString() + "%"});
                    }

                    dataList.addFilterQueryObject(filter);
                } catch(Exception e) {
                    LogUtil.error(getClass().getName(), e, "Error creating filter [" + entry.getKey() + "]");
                }
            }
        }
    }
}
