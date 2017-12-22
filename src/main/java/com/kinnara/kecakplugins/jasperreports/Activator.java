package com.kinnara.kecakplugins.jasperreports;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(JsonJasperReportsMenu.class.getName(), new JsonJasperReportsMenu(), null));
        registrationList.add(context.registerService(JasperReportsMenu.class.getName(), new JasperReportsMenu(), null));
        registrationList.add(context.registerService(JasperReportsJdbcOptions.class.getName(), new JasperReportsJdbcOptions(), null));
        registrationList.add(context.registerService(JasperReportsElement.class.getName(), new JasperReportsElement(), null));
        registrationList.add(context.registerService(JasperReportDataListWebService.class.getName(), new JasperReportDataListWebService(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}