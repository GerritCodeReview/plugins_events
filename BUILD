load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

plugin_name = "events"

gerrit_plugin(
    name = plugin_name,
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: " + plugin_name,
        "Gerrit-ApiVersion: 2.15.21",
        "Implementation-Title: Events Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/" + plugin_name,
        "Gerrit-Module: com.googlesource.gerrit.plugins.events.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.events.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

sh_test(
    name = "docker-tests",
    size = "medium",
    srcs = ["test/docker/run.sh"],
    args = ["--events-plugin-jar", "$(location :events)"],
    data = [plugin_name] + glob(["test/**"]),
    local = True,
)
