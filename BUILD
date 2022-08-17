load("//tools/bzl:junit.bzl", "junit_tests")
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
        "Implementation-Title: Events Plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/" + plugin_name,
        "Gerrit-Module: com.googlesource.gerrit.plugins.events.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.events.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    javacopts = [ "-Werror", "-Xlint:all", "-Xlint:-classfile", "-Xlint:-processing"],
)

junit_tests(
    name = "events_tests",
    size = "small",
    srcs = glob(["src/test/java/**/*Test.java"]),
    tags = ["events"],
    deps = PLUGIN_TEST_DEPS + PLUGIN_DEPS + [
        plugin_name,
    ],
)

sh_test(
    name = "docker-tests",
    size = "medium",
    srcs = ["test/docker/run.sh"],
    args = [
        "--events-plugin-jar",
        "$(location :events)",
    ],
    data = [plugin_name] + glob(["test/**"]),
    local = True,
    tags = ["docker"],
)
