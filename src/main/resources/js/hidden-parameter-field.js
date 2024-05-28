function processParameter(param, paramType) {
    const requestUrl = AJS.contextPath() + '/rest/markdown-from-url/1.0/config/retrieve';

    // sync request for first loading params, then rendering edit-macro
    const request = new XMLHttpRequest();
    request.open('GET', requestUrl, false);
    request.send(null);

    let parameterField = AJS.MacroBrowser.ParameterFields[paramType](param, {});

    if (request.status === 200) {
        parameterField = hideParameter(param, parameterField, !(request.responseText === "true"));
    }

    return parameterField;
}

/// hides parameter field if it is needed
function hideParameter(param, parameterField, toHide){
    if (toHide){
        const parameterField = AJS.MacroBrowser.ParameterFields["_hidden"](param, {});

        if (!parameterField.getValue()) {
            // by default placing null value
            parameterField.setValue(null);
        }
    }

    return parameterField;
}

AJS.MacroBrowser.setMacroJsOverride("markdown-from-url", {
    fields: {
        string: {
            "LinkAzureDevOpsRepository": function (param) {
                return processParameter(param, "string");
            }
        }
    }
});
