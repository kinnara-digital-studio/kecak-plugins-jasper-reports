package com.kinnara.kecakplugins.jasperreports;

import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.DefaultPlugin;
import org.joget.plugin.base.PluginProperty;
import org.joget.plugin.property.model.PropertyEditable;

import java.util.Map;

public class JasperReportsJdbcOptions extends DefaultPlugin implements PropertyEditable {
    public String getName() {
        return "Jasper Reports JDBC Options";
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
        return "Kecak Plugins; Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    public PluginProperty[] getPluginProperties() {
        return null;
    }

    public Object execute(Map properties) {
        return null;
    }

    public String getLabel() {
        return null;
    }

    public String getClassName() {
        return this.getClass().getName();
    }

    public String getPropertyOptions() {
        String json = AppUtil.readPluginResource(getClassName(), "/properties/jasperReportsJdbcOptions.json", new Object[] { JasperReportsJdbcOptions.class.getName()}, true, "message/jasperReportsJdbcOptions");
        return json;
    }

    public Map<String, Object> getProperties() {
        return null;
    }

    public void setProperties(Map<String, Object> properties) {
    }

    public Object getProperty(String property) {
        return null;
    }

    public String getPropertyString(String property) {
        return null;
    }

    public void setProperty(String property, Object value) {
    }
}
