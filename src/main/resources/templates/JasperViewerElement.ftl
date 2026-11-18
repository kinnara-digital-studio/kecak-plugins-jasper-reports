<style type="text/css">
.pnx-icon-pdf-viewer {
    width: 24px !important;
    height: 24px !important;
}
.jasper-export-container {
    margin-top: 6px;
    margin-bottom: 8px;
}
.jasper-export-btn {
    display: inline-block;
    padding: 4px 12px;
    margin-right: 6px;
    margin-bottom: 4px;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.42857143;
    text-align: center;
    white-space: nowrap;
    vertical-align: middle;
    cursor: pointer;
    border: 1px solid #ccc;
    border-radius: 4px;
    background-color: #f7f7f7;
    color: #333;
    text-decoration: none;
}
.jasper-export-btn:hover {
    background-color: #e6e6e6;
    border-color: #adadad;
    color: #333;
    text-decoration: none;
}
</style>

<div class="form-cell" ${elementMetaData!}>
    <#if includeMetaData!>
        <label class="label">${element.properties.label}</label>
        <img class="pnx-icon-pdf-viewer" src="${request.contextPath}/plugin/${className}/images/grid_icon.gif" />
        <span class="form-floating-label">JASPER VIEWER</span>
    <#else>
        <label class="label">${element.properties.label}</label>
        <#if exportUrls?? && (exportUrls?size > 0)>
            <div class="jasper-export-container">
                <#if exportUrls.pdf??>
                    <a href="${request.contextPath!}${exportUrls.pdf}" target="_blank" class="jasper-export-btn export-pdf">Download PDF</a>
                </#if>
                <#if exportUrls.xls??>
                    <a href="${request.contextPath!}${exportUrls.xls}" target="_blank" class="jasper-export-btn export-xls">Download Excel</a>
                </#if>
                <#if exportUrls.docx??>
                    <a href="${request.contextPath!}${exportUrls.docx}" target="_blank" class="jasper-export-btn export-docx">Download Word</a>
                </#if>
            </div>
        </#if>
        <br>
        <embed src="${src}" width="${element.properties.width!320}" height="${element.properties.height!320}" type="application/pdf">
    </#if>
</div>
