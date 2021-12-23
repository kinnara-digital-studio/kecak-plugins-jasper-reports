<style type="text/css">
.pnx-icon-pdf-viewer {
    width: 24px !important;
    height: 24px !important;
}
</style>

<div class="form-cell" ${elementMetaData!}>
    <#if includeMetaData!>
        <label class="label">${element.properties.label}</label>
        <img class="pnx-icon-pdf-viewer" src="${request.contextPath}/plugin/${className}/images/grid_icon.gif" />
        <span class="form-floating-label">JASPER VIEWER</span>
    <#else>
        <label class="label">${element.properties.label}</label>
        <br>
        <embed src="${src}" width="${element.properties.width!320}" height="${element.properties.height!320}" type="application/pdf">
    </#if>
</div>