load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

gerrit_plugin(
    name = "events",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: events",
        "Gerrit-ApiVersion: 2.14.21",
        "Implementation-Title: Events Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/events",
        "Gerrit-Module: com.googlesource.gerrit.plugins.events.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.events.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)
