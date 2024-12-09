package com.kinnara.kecakplugins.jasperreports;

import com.kinnara.kecakplugins.jasperreports.exception.ApiException;
import com.kinnara.kecakplugins.jasperreports.exception.KecakJasperException;
import com.kinnara.kecakplugins.jasperreports.model.ReportSettings;
import com.kinnara.kecakplugins.jasperreports.utils.DataListJasperMixin;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.web.util.WebHtmlResourceHandler;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.BeansException;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JasperViewerElement extends Element implements DataListJasperMixin, PluginWebSupport, FormBuilderPaletteElement {
    final Map<String, Form> formCache = new HashMap<>();

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        final String template = "JasperViewerElement.ftl";

        dataModel.put("className", getClassName());
        dataModel.put("src", getElementValue(formData));
        dataModel.put("ratio", this.getPropertyString("ratio"));

        FormUtil.setReadOnlyProperty(this);

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public Object handleElementValueResponse(@Nonnull Element element, @Nonnull FormData formData) throws JSONException {
        return getElementValue(formData);
    }

    @Override
    public String getFormBuilderCategory() {
        return "Kecak";
    }

    @Override
    public int getFormBuilderPosition() {
        return 100;
    }

    @Override
    public String getFormBuilderIcon() {
        return null;
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<img src='${request.contextPath}/plugin/${className}/images/grid_icon.gif' width='320' height='320/>";
    }

    @Override
    public String getName() {
        return "Jasper Viewer";
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
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{getClassName()};
        String json = AppUtil.readPluginResource(getClassName(), "/properties/jasperViewerElement.json", arguments, true, "message/jasperReports").replaceAll("\"", "'");
        return json;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LogUtil.info(getClass().getName(), "Executing JSON Rest API [" + request.getRequestURI() + "] in method [" + request.getMethod() + "] as [" + WorkflowUtil.getCurrentUsername() + "]");

        try {
            final String action = getRequiredParameter(request, PARAM_ACTION);

            // ROWS DATA
            if ("rows".equals(action)) {
                boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUtil.ROLE_ADMIN);
                if (!isAdmin) {
                    throw new ApiException(HttpServletResponse.SC_UNAUTHORIZED, "User [" + WorkflowUtil.getCurrentUsername() + "] is not admin");
                }

                final String dataListId = getRequiredParameter(request, PARAM_DATALIST_ID);
                final Integer rows = optIntegerParameter(request, PARAM_ROWS).orElse(null);

                final Map<String, List<String>> filters = Optional.of(request.getParameterMap())
                        .map(m -> (Map<String, String[]>) m)
                        .map(Map::entrySet)
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));

                final String sort = getOptionalParameter(request, PARAM_SORT, "");
                final boolean desc = "true".equalsIgnoreCase(getOptionalParameter(request, PARAM_DESC, ""));
                final JSONObject jsonResult = getDataListRow(dataListId, filters, sort, desc, rows);
                response.getWriter().write(jsonResult.toString());

                return;
            }

            // JSON URL
            else if ("getJsonUrl".equals(action)) {
                final String dataListId = getRequiredParameter(request, "dataListId");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("message", request.getRequestURL() + "?" + PARAM_ACTION + "=rows&" + PARAM_DATALIST_ID + "=" + dataListId);

                response.getWriter().write(jsonObject.toString());
                return;
            }

            // REPORT
            else if ("report".equalsIgnoreCase(action)) {
                final String formDefId = getRequiredParameter(request, PARAM_FORM_ID);
                final String elementId = getRequiredParameter(request, PARAM_ELEMENT_ID);
                final String primaryKey = getRequiredParameter(request, "id");
                final String type = getRequiredParameter(request, PARAM_TYPE);
                final String assignmentId = getOptionalParameter(request, PARAM_ASSIGNMENT_ID, "");
                final String processId = getOptionalParameter(request, PARAM_PROCESS_ID, "");

                if ("pdf".equalsIgnoreCase(type)) {
                    final Form form = generateForm(formDefId, formCache);

                    final FormData formData = new FormData();
                    formData.setPrimaryKeyValue(primaryKey);

                    if (!assignmentId.isEmpty()) {
                        formData.setActivityId(assignmentId);
                    }

                    if (!processId.isEmpty()) {
                        formData.setProcessId(processId);
                    }

                    final JasperViewerElement element = (JasperViewerElement) elementStream(form, formData)
                            .filter(e -> elementId.equals(e.getPropertyString("id")) && e instanceof JasperViewerElement)
                            .findFirst()
                            .orElseThrow(() -> new ApiException(HttpServletResponse.SC_NOT_FOUND, "Element [" + elementId + "] is not found in form [" + formDefId + "]"));

                    final String dataListId = element.getPropertyDataListId(null);
                    final DataList dataList = getDataList(dataListId, null);
                    generateReport(element, dataList, formData, type, request, response);
                } else {
                    throw new ApiException(HttpServletResponse.SC_NOT_FOUND, "Jasper type [" + type + "] is not supported");
                }
            }

            // LOAD IMAGE
            else if ("image".equals(action)) {
                final String imageName = getRequiredParameter(request, "image").trim();
                if (!imageName.isEmpty()) {
                    generateImage(request, response, imageName);
                    return;
                }
            }

            // unknown action
            else {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Invalid action [" + action + "]");
            }

            response.setStatus(HttpServletResponse.SC_NO_CONTENT);

        } catch (ApiException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            response.sendError(e.getErrorCode(), e.getMessage());
        } catch (KecakJasperException | JRException | SQLException | JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    protected void generateReport(@Nonnull JasperViewerElement element, @Nonnull DataList dataList, @Nonnull final FormData formData, String type, HttpServletRequest request, HttpServletResponse response) throws JRException, BeansException, SQLException, KecakJasperException {
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final String fileName = element.getFilename();
        final WorkflowAssignment workflowAssignment = workflowManager.getAssignment(formData.getActivityId());

        final String sort = element.getPropertyString("dataListSortBy");
        final boolean desc = "true".equalsIgnoreCase(element.getPropertyString("dataListSortDesc"));
        final boolean useVirtualizer = getPropertyUseVirtualizer(element);
        final String jrxml = getPropertyJrxml(element, workflowAssignment);
        final ReportSettings settings = new ReportSettings(sort, desc, useVirtualizer, jrxml);
        final JasperPrint print = getJasperPrint(element, dataList, workflowAssignment, settings);

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
                    htmlExporter.setConfiguration(configuration);
                }

                htmlExporter.exportReport();
            }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (IOException e) {
            throw new KecakJasperException(e);
        }
    }

    public String getFilename() {
        return ifEmpty(getPropertyFileName(this), getPropertyId(this));
    }

    protected String getElementValue(FormData formData) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final WorkflowAssignment workflowAssignment = Optional.of(formData)
                .map(FormData::getActivityId)
                .map(workflowManager::getAssignment)
                .orElse(null);

        final Form form = FormUtil.findRootForm(this);
        if (form == null) {
            return "";
        }

        final String formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);

        final String assignmentParameter = workflowAssignment == null ? "" : ("&" + PARAM_ASSIGNMENT_ID + "="
                + workflowAssignment.getActivityId()
                + "&" + PARAM_PROCESS_ID + "="
                + workflowAssignment.getProcessId());

        final String url = "/web/json/app/"
                + appDefinition.getAppId() + "/"
                + appDefinition.getVersion() + "/plugin/"
                + getClassName() + "/service?" + PARAM_ACTION + "=report&id="
                + formData.getPrimaryKeyValue() + "&" + PARAM_FORM_ID + "="
                + formDefId + "&" + PARAM_ELEMENT_ID + "="
                + getPropertyString("id") + "&" + PARAM_TYPE + "=pdf"
                + assignmentParameter;

        return AppUtil.processHashVariable(url, workflowAssignment, null, null);
    }


    protected String getPropertyDataListId(WorkflowAssignment assignment) throws KecakJasperException {
        return getRequiredProperty(this, "dataListId", assignment);
    }
}
