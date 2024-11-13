package com.kinnarastudio.kecakplugins.jasperreports;

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
        registrationList.add(context.registerService(DataListJasperMenu.class.getName(), new DataListJasperMenu(), null));
        registrationList.add(context.registerService(DataListJasperTool.class.getName(), new DataListJasperTool(), null));
        registrationList.add(context.registerService(JasperViewerElement.class.getName(), new JasperViewerElement(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}