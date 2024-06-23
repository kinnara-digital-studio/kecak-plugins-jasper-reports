package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnara.kecakplugins.jasperreports.model.ReportSettings;
import com.kinnara.kecakplugins.jasperreports.utils.DataListJasperMixin;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.exception.ApiException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataListJasperTool extends DefaultApplicationPlugin implements DataListJasperMixin, PluginWebSupport {
    final public static String LABEL = "DataList Jasper Tool";

    final Map<String, Form> formCache = new HashMap<>();

    @Override
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
    public Object execute(Map map) {
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) map.get("workflowAssignment");

        try {
            File outputFile = getTempOutputFile(map);
            try (OutputStream fos = Files.newOutputStream(outputFile.toPath());
                 OutputStream bos = new BufferedOutputStream(fos)) {

                final boolean useVirtualizer = getPropertyUseVirtualizer(this);
                final String jrxml = getPropertyJrxml(this, workflowAssignment);
                final String dataListId = getPropertyDataListId(this, workflowAssignment);
                final DataList dataList = getDataList(dataListId, workflowAssignment);
                final ReportSettings setting = new ReportSettings("id", false, useVirtualizer, jrxml);
                final JasperPrint jasperPrint = getJasperPrint(this, dataList, workflowAssignment, setting);

                JasperExportManager.exportReportToPdfStream(jasperPrint, bos);
                LogUtil.info(getClassName(), "Storing temporary file [" + outputFile.getPath() + "] size [" + outputFile.length() + "] bytes");

                final FormData storingFormData = submitForm(map, outputFile);

                // show error
                Optional.of(storingFormData)
                        .map(FormData::getFormErrors)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .forEach(e -> LogUtil.warn(getClass().getName(), "Error field [" + e.getKey() + "] message [" + e.getValue() + "]"));

                if (Optional.of(storingFormData).map(FormData::getFormErrors).map(m -> !m.isEmpty()).orElse(false)) {
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
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{getClassName()};
        String json = AppUtil.readPluginResource(getClassName(), "/properties/dataListJasperTool.json", arguments, true, "messages/jasperReports");
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

            String action = getParameter(request, "action");
            if ("rows".equals(action)) {
                String dataListId = getParameter(request, "dataListId");

                Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                        .map(m -> (Map<String, String[]>) m)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                try {
                    JSONObject jsonResult = getDataListRow(dataListId, filters, null, false);
                    response.getWriter().write(jsonResult.toString());
                    return;
                } catch (KecakJasperException e) {
                    throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
                }
            }

            // get json url
            else if ("getJsonUrl".equals(action)) {
                final String dataListId = optParameter(request, "dataListId", "");
                try {
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
                } catch (JSONException e) {
                    throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
                }
            }

            // unknown action
            else {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Invalid action [" + action + "]");
            }
        } catch (ApiException ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
            response.sendError(ex.getErrorCode(), ex.getMessage());

        }
    }

    private Form getForm() throws KecakJasperException {
        return generateForm(getRequiredProperty(this, "formDefId", null), formCache);
    }

    protected Element getField(Form form, Map properties) throws KecakJasperException {
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        FormData formData = new FormData();

        String fieldId = getRequiredProperty(this, "field", workflowAssignment);
        return Optional.of(fieldId)
                .map(s -> FormUtil.findElement(s, form, formData))
                .filter(e -> e instanceof FileUpload)
                .map(peekMap(e -> LogUtil.info(getClassName(), "Element [" + e.getPropertyString("id") + "] className [" + e.getClassName() + "]")))
                .orElseThrow(() -> new KecakJasperException("Field [" + properties.get("field") + "] is not found in form [" + form.getPropertyString(FormUtil.PROPERTY_ID) + "]"));
    }

    protected File getTempOutputFile(Map properties) throws KecakJasperException {
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

    protected String getPropertyFileName(WorkflowAssignment workflowAssignment) throws KecakJasperException {
        return getRequiredProperty(this, "fileName", workflowAssignment).replaceAll(File.separator, "_");
    }

    protected FormData submitForm(Map properties, File outputFile) throws KecakJasperException {
        PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
        AppService appService = (AppService) pluginManager.getBean("appService");
        WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        FormData formData = new FormData() {{
            if (workflowAssignment != null) {
                setProcessId(workflowAssignment.getProcessId());
                setActivityId(workflowAssignment.getActivityId());
            }

            final String primaryKey = Optional.of("primaryKey")
                    .map(properties::get)
                    .map(String::valueOf)
                    .filter(s -> !s.isEmpty())
                    .orElseGet(() -> {
                        final String processId = getProcessId();

                        // get from record ID
                        return Optional.ofNullable(processId)
                                .map(workflowManager::getProcess)
                                .map(WorkflowProcess::getRecordId)
                                .orElseGet(() -> Optional.ofNullable(processId)
                                        .orElseGet(UuidGenerator.getInstance()::getUuid));
                    });

            setPrimaryKeyValue(primaryKey);
        }};

        Form form = getForm();

        Pattern pattern = Pattern.compile("(?<=app_tempfile/).+");
        Matcher matcher = pattern.matcher(outputFile.getPath());
        if (matcher.find()) {
            final Element fileElement = getField(form, properties);
            final String relativeTempFilePath = matcher.group();
            String fileElementParameterName = FormUtil.getElementParameterName(fileElement);
            formData.addRequestParameterValues(fileElementParameterName, new String[]{relativeTempFilePath});
        }

        // submit form
        return appService.submitForm(form, formData, true);
    }
}
