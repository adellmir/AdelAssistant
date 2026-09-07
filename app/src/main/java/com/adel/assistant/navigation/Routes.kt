package com.adel.assistant.navigation

object Routes {
    const val HOME = "home"

    // ---- نقشه‌برداری / تونل ----
    const val SURVEY_TUNNEL_REPORT = "survey/tunnel/report"
    const val SURVEY_TUNNEL_CHAINAGE = "survey/tunnel/chainage"
    const val SURVEY_TUNNEL_EVENTS = "survey/tunnel/events"
    const val SURVEY_TUNNEL_STATUS = "survey/tunnel/status"

    // ---- نقشه‌برداری / پروژه‌ها ----
    const val SURVEY_PROJECT_REGISTER = "survey/project/register"
    const val SURVEY_PROJECT_EVENTS = "survey/project/events"
    const val SURVEY_PROJECT_CALENDAR = "survey/project/calendar"

    // ---- مالی / تونل ----
    const val FIN_TUNNEL_WORKLOG = "fin/tunnel/worklog"
    const val FIN_TUNNEL_RECEIPTS = "fin/tunnel/receipts"
    const val FIN_TUNNEL_SUMMARY = "fin/tunnel/summary"

    // ---- مالی / پروژه‌ها ----
    const val FIN_PROJECT_INVOICE = "fin/project/invoice"
    const val FIN_PROJECT_RECEIPT = "fin/project/receipt"
    const val FIN_PROJECT_RECEIVABLES = "fin/project/receivables"
    const val FIN_PROJECT_STATUS = "fin/project/status"

    // ---- ابزار ----
    const val TOOL_DXF = "tool/dxf"
    const val TOOL_LINES = "tool/lines"
    const val TOOL_GSI = "tool/gsi"
    const val TOOL_LOCATION = "tool/location"
    const val TOOL_INTERPOLATE = "tool/interpolate"
    const val TOOL_AREA = "tool/area"
    const val TOOL_VOLUME = "tool/volume"
}
