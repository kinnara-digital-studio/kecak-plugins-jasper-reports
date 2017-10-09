package com.kinnara.kecakplugins.jasperreports;

import oracle.sql.TIMESTAMP;
import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.userview.model.Userview;
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 
 * @author aristo
 *
 */
public class JasperReportsJdbcDataListBinder extends DataListBinderDefault {

	public static int MAXROWS = 512;
    public static String ALIAS = "temp";
    public static String SPACE = " ";

    private DataListColumn[] columns;

    public String getName() {
        return "Jasper Reports JDBC Datalist Binder";
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
    	return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    public String getLabel() {
        return getName();
    }

    public String getPropertyOptions() {
    	return AppUtil.readPluginResource(getClassName(), "/properties/JdbcDataListBinder.json", new Object[] { JdbcTestConnectionApi.class.getName() }, true, "/messages/JdbcDataListBinder");
    }

    public DataListColumn[] getColumns() {
        if (this.columns == null) {
            try {
                String sql = this.getQuerySelect(null, this.getProperties(), null, null, null, 0, 1);
                DataSource ds = this.createDataSource();
                this.columns = this.queryMetaData(ds, sql);
            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, "");
                throw new RuntimeException(ex.toString());
            }
        }
        return this.columns;
    }

    public String getPrimaryKeyColumnName() {
        String primaryKey = "";
        @SuppressWarnings("rawtypes")
        Map props = this.getProperties();
        if (props != null) {
            primaryKey = props.get("primaryKey").toString();
        }
        return primaryKey;
    }

    @SuppressWarnings("rawtypes")
	public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
        try {
            DataSource ds = this.createDataSource();
            DataListFilterQueryObject filter = this.processFilterQueryObjects(filterQueryObjects);
            String sql = this.getQuerySelect(dataList, properties, filter, sort, desc, start, rows);
            DataListCollection results = this.executeQuery(dataList, ds, sql, filter.getValues(), start, rows);
            return results;
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "");
            return null;
        }
    }

    public int getDataTotalRowCount(DataList dataList, @SuppressWarnings("rawtypes") Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        try {
            DataSource ds = this.createDataSource();
            DataListFilterQueryObject filter = super.processFilterQueryObjects(filterQueryObjects);
            String sqlCount = this.getQueryCount(dataList, properties, filter);
            LogUtil.debug(this.getClassName(), "###sqlCount :"+sqlCount);
            int count = this.executeQueryCount(dataList, ds, sqlCount, filter.getValues());
            return count;
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "");
            return 0;
        }
    }

    /**
     *
     * @return @throws Exception
     */
    protected DataSource createDataSource() {
    	@SuppressWarnings("rawtypes")
        Map binderProps = this.getProperties();
        DataSource ds = null;
        String datasource = (String)binderProps.get("jdbcDatasource");
        if (datasource != null && "default".equals(datasource)) {
            ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
        } else {
            Properties dsProps = new Properties();
            dsProps.put("driverClassName", binderProps.get("jdbcDriver").toString());
            dsProps.put("url", binderProps.get("jdbcUrl").toString());
            dsProps.put("username", binderProps.get("jdbcUser").toString());
            dsProps.put("password", binderProps.get("jdbcPassword").toString());
            try {
				ds = BasicDataSourceFactory.createDataSource((Properties)dsProps);
			} catch (Exception e) {
				 LogUtil.error(getClassName(), e, e.getMessage());
			}
        }
        return ds;
    }

    protected DataListColumn[] queryMetaData(DataSource ds, String sql) throws SQLException {
        ArrayList<DataListColumn> columns;
        columns = new ArrayList<DataListColumn>();
        try(Connection con = ds.getConnection();
            PreparedStatement pstmt = con.prepareStatement(sql);) {
            String driver = this.getPropertyString("jdbcDriver");
            String datasource = this.getPropertyString("jdbcDatasource");
            if (datasource != null && "default".equals(datasource)) {
                Properties properties = DynamicDataSourceManager.getProperties();
                driver = properties.getProperty("workflowDriver");
            }
            if ("oracle.jdbc.driver.OracleDriver".equals(driver)) {
                pstmt.setMaxRows(1);
                pstmt.executeQuery();
            }
            ResultSetMetaData metaData = pstmt.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; ++i) {
                String name = metaData.getColumnName(i);
                String label = metaData.getColumnLabel(i);
                String type = metaData.getColumnTypeName(i);
                boolean sortable = true;
                DataListColumn column = new DataListColumn(name, label, sortable);
                column.setType(type);
                columns.add(column);
            }
        }
        
        DataListColumn[] columnArray = columns.toArray((DataListColumn[]) new DataListColumn[0]);
        return columnArray;
    }

    protected String getQuerySelect(DataList dataList, @SuppressWarnings("rawtypes") Map properties, DataListFilterQueryObject filterQueryObject, String sort, Boolean desc, Integer start, Integer rows) {
        String sql = properties.get("sql").toString();
        sql = "SELECT * FROM (" + sql + ") " + ALIAS;
        if (filterQueryObject != null) {
            sql = this.insertQueryCriteria(sql, properties, filterQueryObject);
        }
        sql = this.insertQueryOrdering(sql, sort, desc);
        return sql;
    }

    protected String getQueryCount(DataList dataList, @SuppressWarnings("rawtypes") Map properties, DataListFilterQueryObject filterQueryObject) {
    	String sql = properties.get("sqlCount").toString();
    	sql = "SELECT " + getPropertyString("counterColumn") + " FROM (" + sql + ") " + ALIAS;
        sql = this.insertQueryCriteria(sql, properties, filterQueryObject);
        return sql;
    }

    protected String insertQueryCriteria(String sql, @SuppressWarnings("rawtypes") Map properties, DataListFilterQueryObject filterQueryObject) {
        if (sql != null && sql.trim().length() > 0) {
            String keyName = (String) properties.get(Userview.USERVIEW_KEY_NAME);
            String keyValue = (String) properties.get(Userview.USERVIEW_KEY_VALUE);
            String extra = "";
            if (filterQueryObject != null && filterQueryObject.getQuery() != null && filterQueryObject.getQuery().trim().length() > 0) {
                extra = filterQueryObject.getQuery();
            }
            if (sql.contains(USERVIEW_KEY_SYNTAX)) {
                if (keyValue == null) {
                    keyValue = "";
                } else {
                    keyValue = keyValue.trim();
                }
                sql = sql.replaceAll(USERVIEW_KEY_SYNTAX, keyValue);
            } else if (keyName != null && !keyName.isEmpty() && keyValue != null && !keyValue.isEmpty()) {
                if (extra.trim().length() > 0) {
                    extra = extra + "AND ";
                }
                extra = extra + this.getName(keyName) + " = '" + keyValue + "' ";
            }
            if (extra != null && !extra.isEmpty()) {
                sql = sql + " WHERE " + extra;
            }
        }
        return sql;
    }

    protected String insertQueryOrdering(String sql, String sort, Boolean desc) {
        if (sql != null && sql.trim().length() > 0 && sort != null && sort.trim().length() > 0) {
            String clause = " " + this.getName(sort);
            if (desc != null && desc.booleanValue()) {
                clause = clause + " DESC";
            }
            sql = sql + " ORDER BY " + clause;
        }
        return sql;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	protected DataListCollection executeQuery(DataList dataList, DataSource ds, String sql, String[] values, Integer start, Integer rows) throws SQLException {
        DataListCollection results = new DataListCollection();
        try(Connection con = ds.getConnection(); PreparedStatement pstmt = con.prepareStatement(sql);) {
            if (start == null || start < 0) {
                start = 0;
            }
            if (rows != null && rows != -1) {
                int totalRowsToQuery = start + rows;
                pstmt.setMaxRows(totalRowsToQuery);
            }
            if (values != null && values.length > 0) {
                for (int i = 0; i < values.length; ++i) {
                    pstmt.setObject(i + 1, values[i]);
                }
            }
            try(ResultSet rs = pstmt.executeQuery()) {
	            DataListColumn[] columns = this.getColumns();
	            int count = 0;
	            while (rs.next()) {
	                HashMap<String, String> row = new HashMap<String, String>();
	                if (count++ < start) {
	                    continue;
	                }
	                if (columns != null) {
	                    for (DataListColumn column : columns) {
	                        String columnName = column.getName();
	                        Object obj = rs.getObject(columnName);
	                        String columnValue = obj != null ? obj.toString() : "";
	                        if (obj instanceof TIMESTAMP) {
	                            TIMESTAMP timestamp = (TIMESTAMP) obj;
	                            columnValue = timestamp.stringValue();
	                        }
	                        row.put(columnName, columnValue);
	                        //row.put(columnName.toLowerCase(), columnValue);
	                    }
	                }
	                results.add(row);
	            }
            }
        }
        return results;
    }

    protected int executeQueryCount(DataList dataList, DataSource ds, String sql, String[] values) {
        int count = -1;
        
        if (sql != null && sql.trim().length() > 0) {
            try(Connection con = ds.getConnection();
                PreparedStatement pstmt = con.prepareStatement(sql); ) {
                
                
                if (values != null && values.length > 0) {
                    for (int i = 0; i < values.length; ++i) {
                        pstmt.setObject(i + 1, values[i]);
                    }
                }
                
                try(ResultSet rs = pstmt.executeQuery()) {
	                if (rs.next()) {
	                    count = rs.getInt(1);
	                }
                }
            } catch (SQLException e) {
				e.printStackTrace();
			}
        }
        return count;
    }

    public String getClassName() {
        return this.getClass().getName();
    }

    public String getColumnName(String name) {
        if ("dateCreated".equals(name = this.getName(name)) || "dateModified".equals(name)) {
            name = "cast(" + name + " as string)";
        }
        return name;
    }

    protected String getName(String name) {
        if (name != null && !name.isEmpty()) {
            DataListColumn[] columns = this.getColumns();
            for (DataListColumn column : columns) {
                if (!name.equalsIgnoreCase(column.getName())) {
                    continue;
                }
                name = column.getName();
                break;
            }
            name = name.contains(" ") ? ALIAS + ".`" + name + "`" : ALIAS + '.' + name;
        }
        return name;
    }

}
