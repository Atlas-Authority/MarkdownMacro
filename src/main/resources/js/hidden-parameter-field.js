function processParameter(param, paramType) {
    var requestUrl = AJS.contextPath() + '/rest/markdown-from-url/1.0/config/retrieve';

    // sync request for first loading params, then rendering edit-macro
    var request = new XMLHttpRequest();
    request.open('GET', requestUrl, false);
    request.send(null);

    var parameterField = AJS.MacroBrowser.ParameterFields[paramType](param, {});

    if (request.status === 200) {
        parameterField = hideParameter(param, parameterField, !(request.responseText === "true"));
    }

    return parameterField;
}

/// hides parameter field if it is needed
function hideParameter(param, parameterField, toHide){
    if (toHide){
        var parameterField = AJS.MacroBrowser.ParameterFields["_hidden"](param, {});

        if (!parameterField.getValue()) {
            // by default placing false value
            parameterField.setValue("false");
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
        },
        boolean: {
            "UseAzureDevOpsRelativePathUrls": function (param) {
                return processParameter(param, "boolean");
            }
        }
    }
});