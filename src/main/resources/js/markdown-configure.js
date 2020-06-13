AJS.toInit(function ($) {
    var sJson = $("#data").val();
    var json = JSON.parse(sJson);

	if (json.config.enabled) {
		$("#whitelist-enabled").attr("checked", true);
		$("#list-input").attr("disabled", false);
		$("#add-list").attr("disabled", false);
		$("#list-list").attr("disabled", false);
		$("#remove-list").attr("disabled", false);
	} else {
		$("#whitelist-enabled").attr("checked", false);
		$("#list-input").attr("disabled", true);
		$("#add-list").attr("disabled", true);
		$("#list-list").attr("disabled", true);
		$("#remove-list").attr("disabled", true);
	}

    for (var i = 0; i < json.config.whitelist.length; i++) {
        var option = document.createElement("option");
        option.value = json.config.whitelist[i];
        option.innerText = json.config.whitelist[i];
        document.getElementById("list-list").appendChild(option);
    }

	/**
	 * If whitelist-enabled is not checked, disable the input textbox
	 */
	document.getElementById("whitelist-enabled").addEventListener("change", function() {
		var toggle = !document.getElementById("whitelist-enabled").checked;
		document.getElementById("list-input").disabled = toggle;
		document.getElementById("add-list").disabled = toggle;
		document.getElementById("list-list").disabled = toggle;
		document.getElementById("remove-list").disabled = toggle;
	})

    /**
     * Submit button handler
     */
    $("#submit-button").click(function () {
        var json = {
            "config": {
                "whitelist": getIPPatterns("list-list"),
				"enabled": document.getElementById("whitelist-enabled").checked
            }
        };

        $("#data").val(JSON.stringify(json));
        $("#markdown-form").submit();
    });

    /**
     * Add to whitelist button handler
     */
    $("#add-list").click(function () {
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
    $("#remove-list").click(function () {
        removeSelectedOptions(document.getElementById("list-list"));
    });
})(AJS.$);

function removeSelectedOptions(list) {
    var options = list.options;
    for (var i = options.length - 1; i >= 0; i--) {
		if (options[i].selected) {
			list.remove(i);
		}
    }
}

function getIPPatterns(id) {
    var arr = [];
    var list = document.getElementById(id);
    for (var i = 0; i < list.options.length; i++) {
        arr.push(list.options[i].value);
    }
    return arr;
}

function addListClickHandler() {
    var value = $("#list-input").val();
    if (value.trim() !== "") {
		var option = document.createElement("option");
		option.value = value;
		option.innerText = value;
		document.getElementById("list-list").appendChild(option);
    }
    $("#list-input").val("");
}
