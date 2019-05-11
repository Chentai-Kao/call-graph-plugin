package callgraph

enum class ComboBoxOptions(val text: String) {
    VIEW_ALWAYS("Always show"),
    VIEW_HOVERED("When hovered"),
    VIEW_NEVER("Hide"),
    NODE_SELECTION_SINGLE("Single node"),
    NODE_SELECTION_MULTIPLE("Multiple nodes"),
    NODE_COLOR_NONE("None"),
    NODE_COLOR_ACCESS("By access level"),
    NODE_COLOR_CLASS("By class name"),
    DUMMY("(Dummy value)");

    companion object {
        private val reverseMap = values().associateBy(ComboBoxOptions::text)

        @JvmStatic
        fun fromText(text: String) = reverseMap.getValue(text)
    }
}
