import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

enum ComboBoxOptions {
    VIEW_ALWAYS("Always show"),
    VIEW_HOVERED("When hovered"),
    VIEW_NEVER("Hide"),
    NODE_SELECTION_SINGLE("Single node"),
    NODE_SELECTION_MULTIPLE("Multiple nodes"),
    DUMMY("(Dummy value)");

    private final String text;

    ComboBoxOptions(@NotNull String text) {
        this.text = text;
    }

    @NotNull
    String getText() {
        return this.text;
    }

    @NotNull
    static ComboBoxOptions findByText(@NotNull String text) {
        return Arrays.stream(ComboBoxOptions.values())
                .filter(option -> option.getText().equals(text))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Invalid ComboBoxOptions look up: %s", text)));
    }
}
