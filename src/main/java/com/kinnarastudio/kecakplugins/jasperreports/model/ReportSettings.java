package com.kinnarastudio.kecakplugins.jasperreports.model;

public class ReportSettings {
    private final String sort;
    private final boolean desc;
    private final boolean useVirtualizer;
    private final String jrxml;

    public ReportSettings(String sort, boolean desc, boolean useVirtualizer, String jrxml) {
        this.sort = sort == null ? "" : sort;
        this.desc = desc;
        this.useVirtualizer = useVirtualizer;
        this.jrxml = jrxml;
    }

    public String getSort() {
        return sort;
    }

    public boolean isDesc() {
        return desc;
    }

    public boolean isUseVirtualizer() {
        return useVirtualizer;
    }

    public String getJrxml() {
        return jrxml;
    }
}
