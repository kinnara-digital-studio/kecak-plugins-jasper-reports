package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.ApiException;
import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnara.kecakplugins.jasperreports.utils.DataListJasperMixin;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataListJasperTool extends DefaultApplicationPlugin implements DataListJasperMixin, PluginWebSupport {
    @Override
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
    public Object execute(Map map) {
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");

        try {
            File outputFile = getTempOutputFile(map);
            try (OutputStream fos = new FileOutputStream(outputFile);
                 OutputStream bos = new BufferedOutputStream(fos)) {
                JasperPrint jasperPrint = getJasperPrint(this, workflowAssignment);
                JasperExportManager.exportReportToPdfStream(jasperPrint, bos);

                FormData storingFormData = submitForm(map, outputFile);

                // show error
                Optional.of(storingFormData)
                        .map(FormData::getFormErrors)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .forEach(e -> LogUtil.warn(getClass().getName(), "Error field [" + e.getKey() + "] message [" + e.getValue() + "]"));

                if(Optional.of(storingFormData).map(FormData::getFormErrors).map(m -> !m.isEmpty()).orElse(false)) {
                    throw new KecakJasperException("Validation error when storing form data [" + storingFormData.getPrimaryKeyValue() + "]");
                }

            } catch (JRException | IOException e) {
                throw new KecakJasperException(e);
            }
        } catch (KecakJasperException e) {
            LogUtil.error(getClass().getName(), e, e.getMessage());
        }

        return null;
    }

    @Override
    public String getLabel() {
        return "DataList Jasper Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{getClassName()};
        String json = AppUtil.readPluginResource(getClassName(), "/properties/dataListJasperTool.json", arguments, true, "message/jasperReports");
        return json;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LogUtil.info(getClass().getName(), "Executing JSON Rest API [" + request.getRequestURI() + "] in method [" + request.getMethod() + "] as [" + WorkflowUtil.getCurrentUsername() + "]");

        try {
            boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN);
            if (!isAdmin) {
                throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "User [" + WorkflowUtil.getCurrentUsername() + "] is not admin");
            }

            String action = getRequiredParameter(request, "action");
            if ("rows".equals(action)) {
                String dataListId = getRequiredParameter(request, "dataListId");

                Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                        .map(m -> (Map<String, String[]>) m)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                try {
                    JSONObject jsonResult = getDataListRow(dataListId, filters);
                    response.getWriter().write(jsonResult.toString());
                } catch (KecakJasperException e) {
                    throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
                }
            }

            // get json url
            else if ("getJsonUrl".equals(action)) {
                String dataListId = getRequiredParameter(request, "dataListId");
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("message", request.getRequestURL() + "?action=rows&dataListId=" + dataListId);
                    response.getWriter().write(jsonObject.toString());
                } catch (JSONException e) {
                    throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
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

        }
    }

    private Form getForm() throws KecakJasperException {
        return generateForm(getRequiredProperty(this, "formDefId", null));
    }

    private Element getField(Form form, Map properties) throws KecakJasperException {
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        FormData formData = new FormData();

        String fieldId = getRequiredProperty(this,  "field", workflowAssignment);
        return Optional.of(fieldId)
                .map(s -> FormUtil.findElement(s, form, formData))
                .orElseThrow(() -> new KecakJasperException("Field [" + properties.get("field") + "] is not found in form [" + form.getPropertyString(FormUtil.PROPERTY_ID) + "]"));
    }

    private File getTempOutputFile(Map properties) throws KecakJasperException {
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String id = UuidGenerator.getInstance().getUuid();
        String path = id + File.separator;

        String filename = path + getPropertyFileName(workflowAssignment);
        File file = new File(FileManager.getBaseDirectory(), filename);
        if (!file.isDirectory()) {
            // create temp file directory
            new File(FileManager.getBaseDirectory(), path).mkdirs();
            return file;
        }

        throw new KecakJasperException("Cannot write file [" + file.getAbsolutePath() + "]");
    }

    private String getPropertyFileName(WorkflowAssignment workflowAssignment) throws KecakJasperException {
        return getRequiredProperty(this, "fileName", workflowAssignment).replaceAll(File.separator, "_");
    }

    private FormData submitForm(Map properties, File outputFile) throws KecakJasperException {
        PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
        AppService appService = (AppService) pluginManager.getBean("appService");
        FormService formService = (FormService) pluginManager.getBean("formService");
        WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        FormData formData = new FormData();
        if (workflowAssignment != null) {
            formData.setProcessId(workflowAssignment.getProcessId());
            formData.setActivityId(workflowAssignment.getActivityId());
        }

        String primaryKey = Optional.of(formData)
                .map(FormData::getProcessId)
                .map(workflowManager::getWorkflowProcessLink)
                .map(WorkflowProcessLink::getOriginProcessId)
                .orElseGet(() -> Optional.of(formData)
                        .map(FormData::getProcessId)
                        .orElseGet(() -> UuidGenerator.getInstance().getUuid()));

        formData.setPrimaryKeyValue(primaryKey);
        Form form = getForm();

        Element fileElement = getField(form, properties);
        String fileElementParameterName = FormUtil.getElementParameterName(fileElement);

        Pattern pattern = Pattern.compile("(?<=app_tempfile/).+");
        Matcher matcher = pattern.matcher(outputFile.getPath());
        String filename = Optional.of(matcher).filter(Matcher::find).map(Matcher::group).orElse("");

        formData.addRequestParameterValues(fileElementParameterName, new String[]{filename});

        formService.executeFormLoadBinders(form, formData);

        FormRowSet rowSet = formData.getLoadBinderData(form);
        Optional.ofNullable(rowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(FormRow::getCustomProperties)
                .map(m -> (Map<String, String>) m)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)

                .forEach(e -> {
                    Element element = FormUtil.findElement(String.valueOf(e.getKey()), form, formData);
                    if (element != null) {
                        String elementParameterName = FormUtil.getElementParameterName(element);
                        formData.addRequestParameterValues(elementParameterName, new String[]{String.valueOf(e.getValue())});
                    }
                });

        // submit form
        return appService.submitForm(form, formData, false);
    }
}
