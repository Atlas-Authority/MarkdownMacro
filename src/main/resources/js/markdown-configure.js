AJS.toInit(function () {
    const sJson = AJS.$("#data").val();
    const json = JSON.parse(sJson);

    AJS.$("#azure-customs-enabled").attr("checked", json.config.isAzureDevOpsEnabled);

	if (json.config.enabled) {
        AJS.$("#whitelist-enabled").attr("checked", true);
        AJS.$("#list-input").attr("disabled", false);
        AJS.$("#add-list").attr("disabled", false);
        AJS.$("#list-list").attr("disabled", false);
        AJS.$("#remove-list").attr("disabled", false);
	} else {
        AJS.$("#whitelist-enabled").attr("checked", false);
        AJS.$("#list-input").attr("disabled", true);
        AJS.$("#add-list").attr("disabled", true);
        AJS.$("#list-list").attr("disabled", true);
        AJS.$("#remove-list").attr("disabled", true);
	}

	if (sessionStorage.getItem("markdown-config-save-success") === "true" && json.changed) {
        AJS.$("#save-success-popup").show();
		setTimeout(function() {
            AJS.$("#save-success-popup").hide();
		}, 3000);
	} else { AJS.$("#save-success-popup").hide(); }
	sessionStorage.setItem("markdown-config-save-success", "false");

    for (let i = 0; i < json.config.whitelist.length; i++) {
        const option = document.createElement("option");
        option.value = json.config.whitelist[i];
        option.innerText = json.config.whitelist[i];
        document.getElementById("list-list").appendChild(option);
    }

	/**
	 * If whitelist-enabled is not checked, disable the input textbox
	 */
	document.getElementById("whitelist-enabled").addEventListener("change", function() {
		const toggle = !document.getElementById("whitelist-enabled").checked;
		document.getElementById("list-input").disabled = toggle;
		document.getElementById("add-list").disabled = toggle;
		document.getElementById("list-list").disabled = toggle;
		document.getElementById("remove-list").disabled = toggle;
	})

    /**
     * Submit button handler
     */
    AJS.$("#submit-button").click(function () {
        const json = {
            "config": {
                "whitelist": getIPPatterns("list-list"),
				"enabled": document.getElementById("whitelist-enabled").checked,
                "isAzureDevOpsEnabled": document.getElementById("azure-customs-enabled").checked
            },
			"changed": false
        };

        AJS.$("#data").val(JSON.stringify(json));
        AJS.$("#markdown-form").submit();
		sessionStorage.setItem("markdown-config-save-success", "true");
    });

    /**
     * Add to whitelist button handler
     */
    AJS.$("#add-list").click(function () {
		addListClickHandler();
    });

    // Add enter event listener for whitelist
    document.getElementById("list-input").addEventListener("keydown", function (e) {
        if (e.code === 'Enter') {
            e.stopPropagation();
            e.preventDefault();
            addListClickHandler();
        }
    });

    /**
     * Remove from blacklist button handler
     */
    AJS.$("#remove-list").click(function () {
        removeSelectedOptions(document.getElementById("list-list"));
    });
});

function removeSelectedOptions(list) {
    const options = list.options;
    for (let i = options.length - 1; i >= 0; i--) {
		if (options[i].selected) {
			list.remove(i);
		}
    }
}

function getIPPatterns(id) {
    const arr = [];
    const list = document.getElementById(id);
    for (let i = 0; i < list.options.length; i++) {
        arr.push(list.options[i].value);
    }
    return arr;
}

function addListClickHandler() {
    const value = AJS.$("#list-input").val();
    if (value.trim() !== "") {
		const option = document.createElement("option");
		option.value = value;
		option.innerText = value;
		document.getElementById("list-list").appendChild(option);
    }
    AJS.$("#list-input").val("");
}
