import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

enum ViewOptions {
    ALWAYS("Always"),
    HOVERED("Hovered"),
    NEVER("Never");

    private final String text;

    ViewOptions(@NotNull String text) {
        this.text = text;
    }

    @NotNull
    String getText() {
        return this.text;
    }

    @NotNull
    static ViewOptions findByText(@NotNull String text) {
        return Arrays.stream(ViewOptions.values())
                .filter(option -> option.getText().equals(text))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Invalid ViewOptions look up: %s", text)));
    }
}
