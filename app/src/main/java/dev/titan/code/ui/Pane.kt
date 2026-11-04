package dev.titan.code.ui

/** The four full-screen panes (PLAN §4.1), in tab order. */
enum class Pane(val title: String) {
    PROBLEM("Problem"),
    CODE("Code"),
    RESULTS("Results"),
    COMMENTS("Comments"),
}
