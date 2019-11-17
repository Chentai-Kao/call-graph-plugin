# Call Graph Intellij Plugin

This is the open-sourced repo for the [IntelliJ Call Graph plugin](https://plugins.jetbrains.com/plugin/12304-call-graph). Please feel free to leave comments, feedback, and bug reports (on the [Issues tab](https://github.com/Chentai-Kao/call-graph-plugin/issues)).

Pull requests are welcome!

## How to build the plugin (using IntelliJ)
1. Install IntelliJ from the [official website](https://www.jetbrains.com/idea/download/) or whatever makes sense for your operating system.
2. Copy the file `gradle.properties.example` and rename it to `gradle.properties`. This file holds the credential (publish token) for you to publish your local build to Idea plugin repository, and is ignored in version control. This file is required in the Gradle build process, but feel free to leave the sample token value as is. Just remember to replace it with the actual token if you decide to upload the build to the Idea plugin repository.
3. Use IntelliJ to **Open** the root folder of this repo. A Gradle daemon should start building the project.
4. In the Gradle menu, select `call-graph-plugin / Tasks / intelliJ / buildPlugin` to build the plugin.
