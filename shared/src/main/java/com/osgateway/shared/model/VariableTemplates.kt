package com.osgateway.shared.model

object VariableTemplates {
    fun resolve(template: String?, variables: Map<String, String>): String? {
        if (template == null) return null
        var result = template
        variables.forEach { (key, value) ->
            result = result!!.replace("{{$key}}", value, ignoreCase = true)
        }
        return result
    }
}
