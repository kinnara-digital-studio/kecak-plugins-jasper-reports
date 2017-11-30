package com.kinnara.kecakplugins.jasperreports;

import java.io.IOException;
import java.sql.Connection;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

public class JdbcTestConnectionApi implements PluginWebSupport {
	private final static String MESSAGE_PATH = "/messages/JdbcTestConnectionApi";
	
	/**
     * JSON API for test connection button
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException 
     */
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //Limit the API for admin usage only
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String className = request.getParameter("className");
        
        String action = request.getParameter("action");
        if ("testConnection".equals(action)) {
            String message = "";
            Connection conn = null;
            try {
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                
                String jdbcDriver = AppUtil.processHashVariable(request.getParameter("jdbcDriver"), null, null, null, appDef);
                String jdbcUrl = AppUtil.processHashVariable(request.getParameter("jdbcUrl"), null, null, null, appDef);
                String jdbcUser = AppUtil.processHashVariable(request.getParameter("jdbcUser"), null, null, null, appDef);
                String jdbcPassword = AppUtil.processHashVariable(SecurityUtil.decrypt(request.getParameter("jdbcPassword")), null, null, null, appDef);
                
                Properties dsProps = new Properties();
                dsProps.put("driverClassName", jdbcDriver);
                dsProps.put("url", jdbcUrl);
                dsProps.put("username", jdbcUser);
                dsProps.put("password", jdbcPassword);
                DataSource ds = BasicDataSourceFactory.createDataSource(dsProps);
                
                conn = ds.getConnection();
                
                message = AppPluginUtil.getMessage("api.jdbcTestConnectionApi.connectionOk", className, MESSAGE_PATH);
            } catch (Exception e) {
                LogUtil.error(className, e, "Test Connection error");
                message = AppPluginUtil.getMessage("api.jdbcTestConnectionApi.connectionFail", className, MESSAGE_PATH) + "\n" + e.getMessage();
            } finally {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (Exception e) {
                    LogUtil.error(DynamicDataSourceManager.class.getName(), e, "");
                }
            }
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.accumulate("message", message);
                jsonObject.write(response.getWriter());
            } catch (Exception e) {
                //ignore
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }    
}
